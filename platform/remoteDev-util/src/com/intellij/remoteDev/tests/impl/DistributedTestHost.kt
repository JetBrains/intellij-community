package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.diagnostic.DebugLogManager
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.*
import com.intellij.remoteDev.tests.modelGenerated.RdAgentType
import com.intellij.remoteDev.tests.modelGenerated.RdProductType
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import com.intellij.remoteDev.tests.modelGenerated.distributedTestModel
import com.intellij.ui.WinFocusStealer
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.measureTimeMillis
import com.jetbrains.rd.util.reactive.viewNotNull
import com.jetbrains.rd.util.threading.SynchronousScheduler
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.reflect.full.createInstance
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
open class DistributedTestHost(coroutineScope: CoroutineScope) {
  companion object {
    private val LOG = logger<DistributedTestHost>()

    fun getDistributedTestPort(): Int? =
      System.getProperty(AgentConstants.protocolPortPropertyName)?.toIntOrNull()
  }

  open fun setUpLogging(sessionLifetime: Lifetime, session: RdTestSession) {
    LOG.info("Setting up loggers")
    LogFactoryHandler.bindSession<AgentTestLoggerFactory>(sessionLifetime, session)
  }

  protected open fun assertLoggerFactory() {
    LogFactoryHandler.assertLoggerFactory<AgentTestLoggerFactory>()
  }

  private val projectOrNull: Project?
    get() = ProjectManagerEx.getOpenProjects().singleOrNull()
  val project: Project
    get() = projectOrNull!!

  init {
    val hostAddress =
      System.getProperty(AgentConstants.protocolHostPropertyName)?.let {
        LOG.info("${AgentConstants.protocolHostPropertyName} system property is set=$it, will try to get address from it.")
        // this won't work when we do custom network setups as the default gateway will be overridden
        // val hostEntries = File("/etc/hosts").readText().lines()
        // val dockerInterfaceEntry = hostEntries.last { it.isNotBlank() }
        // val ipAddress = dockerInterfaceEntry.split("\\s".toRegex()).first()
        //  host.docker.internal is not available on linux yet (20.04+)
        InetAddress.getByName(it)
      } ?: InetAddress.getLoopbackAddress()

    val port = getDistributedTestPort()
    if (port != null) {
      LOG.info("Queue creating protocol on $hostAddress:$port")
      coroutineScope.launch {
        while (!LoadingState.COMPONENTS_LOADED.isOccurred) {
          delay(10.milliseconds)
        }
        withContext(Dispatchers.EDT) {
          createProtocol(hostAddress, port)
        }
      }
    }
  }

  private fun Application.flushQueueFromAnyThread() {
    if (isDispatchThread) {
      // Flush all events to process pending protocol events and other things
      //   before actual test method execution
      IdeEventQueue.getInstance().flushQueue()
    }
    else {
      UIUtil.pump()
    }
  }

  private fun createProtocol(hostAddress: InetAddress, port: Int) {
    LOG.info("Creating protocol...")

    // EternalLifetime.createNested() is used intentionally to make sure logger session's lifetime is not terminated before the actual application stop.
    val lifetime = EternalLifetime.createNested()

    val wire = SocketWire.Client(lifetime, DistributedTestIdeScheduler, port, AgentConstants.protocolName, hostAddress)
    val protocol = Protocol(name = AgentConstants.protocolName,
                            serializers = Serializers(),
                            identity = Identities(IdKind.Client),
                            scheduler = DistributedTestIdeScheduler,
                            wire = wire,
                            lifetime = lifetime)
    val model = protocol.distributedTestModel

    LOG.info("Advise for session...")
    model.session.viewNotNull(lifetime) { sessionLifetime, session ->
      try {
        setUpLogging(sessionLifetime, session)
        val app = ApplicationManager.getApplication()
        if (session.testMethodName == null || session.testClassName == null) {
          LOG.info("Test session without test class to run.")
        }
        else {
          LOG.info("New test session: ${session.testClassName}.${session.testMethodName}")

          // Needed to enable proper focus behaviour
          if (SystemInfoRt.isWindows) {
            WinFocusStealer.setFocusStealingEnabled(true)
          }

          // Create test class
          val testClass = Class.forName(session.testClassName)
          val testClassObject = testClass.kotlin.createInstance() as DistributedTestPlayer

          // Tell test we are running it inside an agent
          val agentInfo = AgentInfo(session.agentInfo, session.testClassName, session.testMethodName)
          val queue = testClassObject.initAgent(agentInfo)

          // Play test method
          val testMethod = testClass.getMethod(session.testMethodName)
          testClassObject.performInit(testMethod)
          testMethod.invoke(testClassObject)

          fun runAction(agentAction: AgentAction, expectIsDispatchThread: Boolean): RdTask<String?> {
            val actionTitle = agentAction.title
            try {
              assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local when test method starts" }
              assert(app.isDispatchThread == expectIsDispatchThread) {
                "Expected to be started on EDT: $expectIsDispatchThread, actual: ${Thread.currentThread()}"
              }

              if (expectIsDispatchThread) {
                LOG.info("'$actionTitle': preparing to start action")

                val isNotRdHost = !(session.agentInfo.productTypeType == RdProductType.REMOTE_DEVELOPMENT && session.agentInfo.agentType == RdAgentType.HOST)
                if (!app.isHeadlessEnvironment && isNotRdHost) {
                  app.flushQueueFromAnyThread()
                  requestFocus(actionTitle)
                }
              }

              showNotification("${session.agentInfo.id}: $actionTitle")
              // Flush all events to process pending protocol events and other things
              //   before actual test method execution
              app.flushQueueFromAnyThread()

              val agentContext = when (session.agentInfo.agentType) {
                RdAgentType.HOST -> HostAgentContextImpl(session.agentInfo, protocol)
                RdAgentType.CLIENT -> ClientAgentContextImpl(session.agentInfo, protocol)
                RdAgentType.GATEWAY -> GatewayAgentContextImpl(session.agentInfo, protocol)
              }

              // Execute test method
              lateinit var result: RdTask<String?>
              LOG.info("'$actionTitle': starting action")
              val elapsedAction = measureTimeMillis {
                result = agentAction.action.invoke(agentContext)
              }
              LOG.info("'$actionTitle': completed action in ${elapsedAction}ms")

              projectOrNull?.let {
                // Sync state across all IDE agents to maintain proper order in protocol events
                LOG.info("'$actionTitle': Sync protocol events after execution...")
                val elapsedSync = measureTimeMillis {
                  DistributedTestBridge.getInstance(it).syncProtocolEvents()
                  app.flushQueueFromAnyThread()
                }
                LOG.info("'$actionTitle': Protocol state sync completed in ${elapsedSync}ms")
              }

              // Assert state
              assertLoggerFactory()

              return result
            }
            catch (ex: Throwable) {
              val msg = "${session.agentInfo.id}: ${actionTitle.let { "'$it' " }.orEmpty()}hasn't finished successfully"
              LOG.warn(msg, ex)
              if (!app.isHeadlessEnvironment) {
                makeScreenshot(actionTitle)
              }
              return RdTask.faulted(AssertionError(msg, ex))
            }
          }

          // Advice for processing events
          session.runNextAction.set { _, _ ->
            runAction(queue.remove(), true)
          }

          // Special handler to be used in
          session.runNextActionBackground.set(SynchronousScheduler, SynchronousScheduler) { _, _ ->
            runAction(queue.remove(), false)
          }
        }

        session.isResponding.set { _, _ ->
          LOG.info("Answering for session is responding...")
          RdTask.fromResult(true)
        }

        session.closeProject.set { _, _ ->
          when (projectOrNull) {
            null ->
              return@set RdTask.faulted(IllegalStateException("${session.agentInfo.id}: Nothing to close"))
            else -> {
              LOG.info("Close project...")
              try {
                ProjectManagerEx.getInstanceEx().forceCloseProject(project)
                return@set RdTask.fromResult(true)
              }
              catch (e: Exception) {
                LOG.warn("Error on project closing", e)
                return@set RdTask.fromResult(false)
              }
            }
          }
        }

        session.closeProjectIfOpened.set { _, _ ->
          LOG.info("Close project if it is opened...")
          projectOrNull?.let {
            try {
              ProjectManagerEx.getInstanceEx().forceCloseProject(project)
              return@set RdTask.fromResult(true)
            }
            catch (e: Exception) {
              LOG.warn("Error on project closing", e)
              return@set RdTask.fromResult(false)
            }
          } ?: return@set RdTask.fromResult(true)

        }

        session.shutdown.advise(lifetime) {
          LOG.info("Shutdown application...")
          app.exit(true, true, false)
        }

        session.makeScreenshot.set { fileName ->
          return@set makeScreenshot(fileName)
        }

        // Initialize loggers
        DebugLogManager.getInstance().applyCategories(
          session.traceCategories.map { DebugLogManager.Category(it, DebugLogManager.DebugLogLevel.TRACE) } +
          session.debugCategories.map { DebugLogManager.Category(it, DebugLogManager.DebugLogLevel.DEBUG) }
        )
        LOG.info("Test session ready!")
        session.ready.value = true
      }
      catch (ex: Throwable) {
        LOG.warn("Test session initialization hasn't finished successfully", ex)
        session.ready.value = false
      }
    }
  }

  private fun requestFocus(actionTitle: String) {
    projectOrNull?.let {
      val frame = WindowManager.getInstance().getFrame(it)
      if (frame != null) {
        if (frame.isFocusAncestor()) {
          LOG.info("'$actionTitle': Already focused")
        }
        else {
          LOG.info("'$actionTitle': Requesting project focus")
          ProjectUtil.focusProjectWindow(it, true)
          if (!frame.isFocusAncestor()) {
            LOG.error("Failed to request the focus.")
          }
        }
      }
      else {
        LOG.info("'$actionTitle': No frame yet, nothing to focus")
      }
    }
  }

  private fun makeScreenshot(actionName: String): Boolean {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      error("Don't try making screenshots on application in headless mode.")
    }

    val timeNow = LocalDateTime.now()
    val buildStartTimeString = timeNow.format(DateTimeFormatter.ofPattern("HHmmss"))
    val maxActionLength = 30

    fun screenshotFile(suffix: String? = null): File {
      var fileName =
        actionName
          .replace("[^a-zA-Z.]".toRegex(), "_")
          .replace("_+".toRegex(), "_")
          .take(maxActionLength)

      if (suffix != null) {
        fileName += suffix
      }

      fileName += "_at_$buildStartTimeString"

      if (!fileName.endsWith(".png")) {
        fileName += ".png"
      }
      return File(PathManager.getLogPath()).resolve(fileName)
    }

    val result = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeLater {
      fun makeScreenshotOfComponent(screenshotFile: File, component: Component) {
        LOG.info("Making screenshot of ${component}")
        val img = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        component.printAll(img.createGraphics())
        ApplicationManager.getApplication().executeOnPooledThread {
          try {
            ImageIO.write(img, "png", screenshotFile)
            LOG.info("Screenshot is saved at: $screenshotFile")
          }
          catch (t: Throwable) {
            LOG.warn("Exception while writing screenshot image to file", t)
          }
        }
      }

      val windows = Window.getWindows().filter { it.height != 0 && it.width != 0 }.filter { it.isShowing }
      windows.forEachIndexed { index, window ->
        val screenshotFile = if (window.isFocusAncestor()) {
          screenshotFile("_${index}_focusedWindow")
        }
        else {
          screenshotFile("_$index")
        }
        makeScreenshotOfComponent(screenshotFile, window)
      }
      result.complete(true)
    }

    IdeEventQueue.getInstance().flushQueue()

    try {
      result[45, TimeUnit.SECONDS]
    }
    catch (e: Throwable) {
      when (e) {
        is InterruptedException, is ExecutionException, is TimeoutException -> LOG.info(e)
        else -> {
          LOG.warn("Test action 'makeScreenshot' hasn't finished successfully", e)
          return false
        }
      }
    }
    return result.get()
  }
}

@Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
private fun showNotification(text: String?): Notification? {
  if (ApplicationManager.getApplication().isHeadlessEnvironment || text.isNullOrBlank()) {
    return null
  }

  val notification = Notification("TestFramework",
                                  "Test Framework",
                                  text,
                                  NotificationType.INFORMATION)
  Notifications.Bus.notify(notification)
  return notification
}
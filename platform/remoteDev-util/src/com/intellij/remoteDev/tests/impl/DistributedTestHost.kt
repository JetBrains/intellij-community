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
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
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
import com.jetbrains.rd.framework.util.launch
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.measureTimeMillis
import com.jetbrains.rd.util.reactive.viewNotNull
import com.jetbrains.rd.util.threading.SynchronousScheduler
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.reflect.full.createInstance
import kotlin.time.Duration.Companion.milliseconds

@TestOnly
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
    LOG.info("Flush queue...")
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

    LOG.info("Advise for session. Current state: ${model.session.value}...")
    model.session.viewNotNull(lifetime) { sessionLifetime, session ->
      val isNotRdHost = !(session.agentInfo.productTypeType == RdProductType.REMOTE_DEVELOPMENT && session.agentInfo.agentType == RdAgentType.HOST)

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
          val (queue, bgQueue) = testClassObject.initAgent(agentInfo)

          // Play test method
          val testMethod = testClass.getMethod(session.testMethodName)
          testClassObject.performInit(testMethod)
          testMethod.invoke(testClassObject)

          fun runAction(agentAction: AgentAction): RdTask<String?> {
            val actionTitle = agentAction.title
            val expectBlockedEdt = agentAction.expectBlockedEdt
            try {
              assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local when test method starts" }

              LOG.info("'$actionTitle': preparing to start action on ${Thread.currentThread().name}, " +
                       "expectBlockedEdt=$expectBlockedEdt")

              if (app.isDispatchThread) {
                projectOrNull?.let {
                  // Sync state across all IDE agents to maintain proper order in protocol events
                  LOG.info("'$actionTitle': Sync protocol events before execution...")
                  val elapsedSync = measureTimeMillis {
                    DistributedTestBridge.getInstance().syncProtocolEvents()
                  }
                  LOG.info("'$actionTitle': Protocol state sync completed in ${elapsedSync}ms")
                }
              }

              if (!expectBlockedEdt) {
                app.flushQueueFromAnyThread()
              }

              if (!app.isHeadlessEnvironment && isNotRdHost && app.isDispatchThread) {
                requestFocus(actionTitle)
              }

              showNotification("${session.agentInfo.id}: $actionTitle")

              val agentContext = when (session.agentInfo.agentType) {
                RdAgentType.HOST -> HostAgentContextImpl(session.agentInfo, protocol, lifetime)
                RdAgentType.CLIENT -> ClientAgentContextImpl(session.agentInfo, protocol, lifetime)
                RdAgentType.GATEWAY -> GatewayAgentContextImpl(session.agentInfo, protocol, lifetime)
              }

              // Execute test method
              lateinit var result: RdTask<String?>
              LOG.info("'$actionTitle': starting action")
              val elapsedAction = measureTimeMillis {
                result = agentAction.action.invoke(agentContext)
              }
              LOG.info("'$actionTitle': completed action in ${elapsedAction}ms")

              // Assert state
              assertLoggerFactory()

              return result
            }
            catch (ex: Throwable) {
              val msg = "${session.agentInfo.id}: ${actionTitle.let { "'$it' " }}hasn't finished successfully"
              LOG.warn(msg, ex)
              if (!app.isHeadlessEnvironment && isNotRdHost) {
                runBlockingCancellable {
                  lifetime.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) { // even if there is a modal window opened
                    makeScreenshot(actionTitle)
                  }
                }
              }
              return RdTask.faulted(AssertionError(msg, ex))
            }
          }

          // Advice for processing events
          session.runNextAction.set { _, _ ->
            runAction(queue.remove())
          }

          // Special handler to be used in
          session.runNextActionBackground.set(SynchronousScheduler, SynchronousScheduler) { _, _ ->
            runAction(bgQueue.remove())
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

        session.requestFocus.set { actionTitle ->
          return@set requestFocus(actionTitle)
        }

        session.makeScreenshot.set { fileName ->
          return@set makeScreenshot(fileName)
        }

        session.showNotification.advise(sessionLifetime) { actionTitle ->
          showNotification("${session.agentInfo.id}: $actionTitle")
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


  private fun requestFocus(actionTitle: String): Boolean {
    val currentProject = projectOrNull

    val windowToFocus =
      if (currentProject != null) {
        val ideFrame = WindowManager.getInstance().getFrame(currentProject)
        if (ideFrame == null) {
          LOG.info("'$actionTitle': No frame yet, nothing to focus")
          return false
        }
        else {
          ideFrame
        }
      }
      else {
        val windows = Window.getWindows()
        if (windows.size != 1) {
          LOG.info("'$actionTitle': Can't choose a frame to focus. All windows: ${windows.joinToString(", ")}")
          return false
        }
        else {
          windows.single()
        }
      }

    val windowString = "window '${windowToFocus.name}'"
    if (windowToFocus.isFocusAncestor()) {
      LOG.info("'$actionTitle': window '$windowString' is already focused")
      return true
    }
    else {
      LOG.info("'$actionTitle': Requesting project focus for '$windowString'")
      ProjectUtil.focusProjectWindow(projectOrNull, true)
      if (!windowToFocus.isFocusAncestor()) {
        LOG.error("Failed to request the focus.")
        return false
      }
      return true
    }
  }

  private fun makeScreenshot(actionName: String): Boolean {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Can't make screenshot on application in headless mode.")
      return false
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

    try {
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
    return true
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
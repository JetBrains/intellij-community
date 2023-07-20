package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.diagnostic.DebugLogManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.*
import com.intellij.remoteDev.tests.modelGenerated.RdAgentType
import com.intellij.remoteDev.tests.modelGenerated.RdProductType
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import com.intellij.remoteDev.tests.modelGenerated.distributedTestModel
import com.intellij.ui.WinFocusStealer
import com.intellij.util.application
import com.intellij.util.ui.ImageUtil
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.measureTimeMillis
import com.jetbrains.rd.util.reactive.viewNotNull
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.reflect.full.createInstance

@ApiStatus.Internal
open class DistributedTestHost {
  companion object {
    private val logger = Logger.getInstance(DistributedTestHost::class.java)

    const val screenshotOnFailureFileName = "ScreenshotOnFailure"

    fun getDistributedTestPort(): Int? =
      (System.getProperty(AgentConstants.protocolPortEnvVar)
       ?: System.getenv(AgentConstants.protocolPortEnvVar))?.toIntOrNull()
  }

  open fun setUpLogging(sessionLifetime: Lifetime, session: RdTestSession) {
    logger.info("Setting up loggers")
    LogFactoryHandler.bindSession<AgentTestLoggerFactory>(sessionLifetime, session)
  }

  protected open fun assertLoggerFactory() {
    LogFactoryHandler.assertLoggerFactory<AgentTestLoggerFactory>()
  }

  val projectOrNull: Project?
    get() = ProjectManagerEx.getOpenProjects().singleOrNull()
  val project: Project
    get() = projectOrNull!!

  init {
    val hostAddress = when (SystemInfo.isLinux) {
      true -> System.getenv(AgentConstants.dockerHostIpEnvVar)?.let {
        logger.info("${AgentConstants.dockerHostIpEnvVar} env var is set=$it, will try to get address from it.")
        // this won't work when we do custom network setups as the default gateway will be overridden
        // val hostEntries = File("/etc/hosts").readText().lines()
        // val dockerInterfaceEntry = hostEntries.last { it.isNotBlank() }
        // val ipAddress = dockerInterfaceEntry.split("\\s".toRegex()).first()
        InetAddress.getByName(it)
      } ?: InetAddress.getLoopbackAddress()
      false -> InetAddress.getLoopbackAddress()
    }

    val port = getDistributedTestPort()

    if (port != null) {
      logger.info("Queue creating protocol on $hostAddress:$port")
      application.invokeLater { createProtocol(hostAddress, port) }
    }
  }

  private fun createProtocol(hostAddress: InetAddress, port: Int) {
    logger.info("Creating protocol...")

    // EternalLifetime.createNested() is used intentionally to make sure logger session's lifetime is not terminated before the actual application stop.
    val lifetime = EternalLifetime.createNested()

    val wire = SocketWire.Client(lifetime, DistributedTestIdeScheduler, port, AgentConstants.protocolName, hostAddress)
    val protocol =
      Protocol(AgentConstants.protocolName, Serializers(), Identities(IdKind.Client), DistributedTestIdeScheduler, wire, lifetime)
    val model = protocol.distributedTestModel

    logger.info("Advise for session...")
    model.session.viewNotNull(lifetime) { sessionLifetime, session ->
      try {
        setUpLogging(sessionLifetime, session)
        if (session.testMethodName == null || session.testClassName == null) {
          logger.info("Test session without test class to run.")
        }
        else {
          logger.info("New test session: ${session.testClassName}.${session.testMethodName}")

          // Needed to enable proper focus behaviour
          if (SystemInfo.isWindows) {
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

          // Advice for processing events
          session.runNextAction.set { _, _ ->
            var actionTitle: String? = null
            try {
              assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local when test method starts" }

              val action = queue.remove()
              actionTitle = action.title


              logger.info("'$actionTitle': preparing to start action")

              val isNotRdHost = !(session.agentInfo.productTypeType == RdProductType.REMOTE_DEVELOPMENT && session.agentInfo.agentType == RdAgentType.HOST)
              if (!application.isHeadlessEnvironment && isNotRdHost) {
                IdeEventQueue.getInstance().flushQueue()
                requestFocus(actionTitle)
              }

              showNotification("${session.agentInfo.id}: $actionTitle")
              // Flush all events to process pending protocol events and other things
              //   before actual test method execution
              IdeEventQueue.getInstance().flushQueue()

              // Execute test method
              lateinit var result: RdTask<String?>
              val context = when (session.agentInfo.agentType) {
                RdAgentType.HOST -> HostAgentContextImpl(session.agentInfo, application, projectOrNull, protocol)
                RdAgentType.CLIENT -> ClientAgentContextImpl(session.agentInfo, application, projectOrNull, protocol)
                RdAgentType.GATEWAY -> GatewayAgentContextImpl(session.agentInfo, application, projectOrNull, protocol)
              }
              logger.info("'$actionTitle': starting action")
              val elapsedAction = measureTimeMillis {
                result = action.action.invoke(context)
              }
              logger.info("'$actionTitle': completed action in ${elapsedAction}ms")

              projectOrNull?.let {
                // Sync state across all IDE agents to maintain proper order in protocol events
                logger.info("'$actionTitle': Sync protocol events after execution...")
                val elapsedSync = measureTimeMillis {
                  DistributedTestBridge.getInstance(it).syncProtocolEvents()
                  IdeEventQueue.getInstance().flushQueue()
                }
                logger.info("'$actionTitle': Protocol state sync completed in ${elapsedSync}ms")
              }

              // Assert state
              assertLoggerFactory()

              return@set result
            }
            catch (ex: Throwable) {
              val msg = "${session.agentInfo.id}: ${actionTitle?.let { "'$it' " }.orEmpty()}hasn't finished successfully"
              logger.warn(msg, ex)
              if (!application.isHeadlessEnvironment)
                actionTitle?.let { makeScreenshot("${it}_$screenshotOnFailureFileName") }
              return@set RdTask.faulted(AssertionError(msg, ex))
            }
          }
        }

        session.isResponding.set { _, _ ->
          logger.info("Answering for session is responding...")
          RdTask.fromResult(true)
        }

        session.closeProject.set { _, _ ->
          when (projectOrNull) {
            null ->
              return@set RdTask.faulted(IllegalStateException("${session.agentInfo.id}: Nothing to close"))
            else -> {
              logger.info("Close project...")
              try {
                ProjectManagerEx.getInstanceEx().forceCloseProject(project)
                return@set RdTask.fromResult(true)
              }
              catch (e: Exception) {
                logger.warn("Error on project closing", e)
                return@set RdTask.fromResult(false)
              }
            }
          }
        }

        session.closeProjectIfOpened.set { _, _ ->
          logger.info("Close project if it is opened...")
          projectOrNull?.let {
            try {
              ProjectManagerEx.getInstanceEx().forceCloseProject(project)
              return@set RdTask.fromResult(true)
            }
            catch (e: Exception) {
              logger.warn("Error on project closing", e)
              return@set RdTask.fromResult(false)
            }
          } ?: return@set RdTask.fromResult(true)

        }

        session.shutdown.advise(lifetime) {
          logger.info("Shutdown application...")
          application.exit(true, true, false)
        }

        session.makeScreenshot.set { fileName ->
          return@set makeScreenshot(fileName)
        }

        // Initialize loggers
        DebugLogManager.getInstance().applyCategories(
            session.traceCategories.map { DebugLogManager.Category(it, DebugLogManager.DebugLogLevel.TRACE)} +
            session.debugCategories.map { DebugLogManager.Category(it, DebugLogManager.DebugLogLevel.DEBUG) }
          )
        logger.info("Test session ready!")
        session.ready.value = true
      }
      catch (ex: Throwable) {
        logger.warn("Test session initialization hasn't finished successfully", ex)
        session.ready.value = false
      }
    }
  }

  private fun requestFocus(actionTitle: String) {
    projectOrNull?.let {
      val frame = WindowManager.getInstance().getFrame(it)
      if (frame != null) {
        if (frame.isFocusAncestor()) {
          logger.info("'$actionTitle': Already focused")
        }
        else {
          logger.info("'$actionTitle': Requesting project focus")
          ProjectUtil.focusProjectWindow(it, true)
          if (!frame.isFocusAncestor()) {
            logger.error("Failed to request the focus.")
          }
        }
      }
      else {
        logger.info("'$actionTitle': No frame yet, nothing to focus")
      }
    }
  }

  private fun makeScreenshot(actionName: String): Boolean {
    if (application.isHeadlessEnvironment) {
      error("Don't try making screenshots on application in headless mode.")
    }

    fun screenshotFile(suffix: String? = null): File {
      var fileName = actionName.replace("[^a-zA-Z.]".toRegex(), "_").replace("_+".toRegex(), "_")
      if (suffix != null) {
        fileName += suffix
      }
      if (!fileName.endsWith(".png")) {
        fileName += ".png"
      }
      return File(PathManager.getLogPath()).resolve(fileName)
    }

    val result = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeLater {
      fun makeScreenshotOfComponent(screenshotFile: File, component: Component?) {
        if (component != null) {
          logger.info("Making screenshot")
          val img = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
          component.printAll(img.createGraphics())
          ApplicationManager.getApplication().executeOnPooledThread {
            try {
              ImageIO.write(img, "png", screenshotFile)
              logger.info("Screenshot is saved at: $screenshotFile")
            }
            catch (t: Throwable) {
              logger.warn("Exception while writing screenshot image to file", t)
            }
          }
        }
        else {
          logger.warn("Frame was empty when makeScreenshot was called")
        }
      }

      val focusedComponent = WindowManager.getInstance().getFocusedComponent(project)?.focusCycleRootAncestor
      val projectFrame = WindowManager.getInstance().getIdeFrame(projectOrNull)?.component
      if (focusedComponent != projectFrame) {
        makeScreenshotOfComponent(screenshotFile("_focusedWindow"), focusedComponent)
      }
      val screenshotFile = screenshotFile()
      makeScreenshotOfComponent(screenshotFile, projectFrame)
      result.complete(screenshotFile.exists())
    }

    IdeEventQueue.getInstance().flushQueue()

    try {
      result[45, TimeUnit.SECONDS]
    }
    catch (e: Throwable) {
      when (e) {
        is InterruptedException, is ExecutionException, is TimeoutException -> logger.info(e)
        else -> {
          logger.warn("Test action 'makeScreenshot' hasn't finished successfully", e)
          return false
        }
      }
    }
    return result.get()
  }

  @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
  private fun showNotification(text: String?): Notification? {
    if (application.isHeadlessEnvironment || text.isNullOrBlank())
      return null

    val notification = Notification("TestFramework",
                                    "Test Framework",
                                    text,
                                    NotificationType.INFORMATION)
    Notifications.Bus.notify(notification)
    return notification
  }
}
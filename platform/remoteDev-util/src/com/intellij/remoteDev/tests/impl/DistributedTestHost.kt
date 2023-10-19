package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
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
import com.jetbrains.rd.framework.*
import com.intellij.openapi.rd.util.setSuspendPreserveClientId
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.viewNotNull
import com.jetbrains.rd.util.threading.asRdScheduler
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import java.time.LocalTime
import javax.imageio.ImageIO
import kotlin.reflect.full.createInstance
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@TestOnly
@ApiStatus.Internal
open class DistributedTestHost(coroutineScope: CoroutineScope) {
  companion object {
    // it is easier to sort out logs from just testFramework
    private val LOG = Logger.getInstance(RdctTestFrameworkLoggerCategory.category + "Host")

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

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun createProtocol(hostAddress: InetAddress, port: Int) {
    LOG.info("Creating protocol...")
    enableCoroutineDump()

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
      val isNotRdHost = !(session.agentInfo.productType == RdProductType.REMOTE_DEVELOPMENT && session.agentInfo.agentType == RdAgentType.HOST)

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

          // Advice for processing events
          session.runNextAction.setSuspendPreserveClientId { _, _ ->
            val action = queue.remove()
            val actionTitle = action.title
            val timeout = action.timeout
            val requestFocus = action.fromEdt
            try {
              assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local when test method starts" }

              LOG.info("'$actionTitle': received action execution request")

              val dispatcher = if (action.fromEdt) Dispatchers.EDT else Dispatchers.Default
              return@setSuspendPreserveClientId withContext(dispatcher) {
                if (action.fromEdt) {
                  // Sync state across all IDE agents to maintain proper order in protocol events
                  // we don't wat to sync state in case of bg task, as it may be launched with blocked UI thread
                  runLogged("'$actionTitle': Sync protocol events before execution") {
                    withTimeout(3.minutes) {
                      DistributedTestBridge.getInstance().syncProtocolEvents()
                    }
                  }
                }

                if (!app.isHeadlessEnvironment && isNotRdHost && requestFocus) {
                  requestFocus(actionTitle)
                }

                showNotification("${session.agentInfo.id}: $actionTitle")

                val agentContext = when (session.agentInfo.agentType) {
                  RdAgentType.HOST -> HostAgentContextImpl(session.agentInfo, protocol)
                  RdAgentType.CLIENT -> ClientAgentContextImpl(session.agentInfo, protocol)
                  RdAgentType.GATEWAY -> GatewayAgentContextImpl(session.agentInfo, protocol)
                }

                val result = runLogged(actionTitle, timeout) {
                  action.action(agentContext)
                }

                // Assert state
                assertLoggerFactory()

                result
              }
            }
            catch (ex: Throwable) {
              LOG.warn("${session.agentInfo.id}: ${actionTitle.let { "'$it' " }}hasn't finished successfully", ex)
              if (!app.isHeadlessEnvironment && isNotRdHost) {
                makeScreenshot(actionTitle)
              }
              throw ex
            }
          }
        }

        session.isResponding.setSuspendPreserveClientId { _, _ ->
          LOG.info("Answering for session is responding...")
          true
        }

        session.closeProjectIfOpened.setSuspendPreserveClientId { _, _ ->
          runLogged("Close project if it is opened") {
            projectOrNull?.let {
              withContext(Dispatchers.EDT + ModalityState.any().asContextElement() + NonCancellable) {
                ProjectManagerEx.getInstanceEx().forceCloseProject(project)
              }
            } ?: true
          }
        }

        session.shutdown.adviseOn(lifetime, Dispatchers.Default.asRdScheduler) {
          runBlockingCancellable {
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement() + NonCancellable) {
              LOG.info("Shutting down the application...")
              app.exit(true, true, false)
            }
          }
        }

        session.requestFocus.setSuspendPreserveClientId { _, actionTitle ->
          withContext(Dispatchers.EDT) {
            requestFocus(actionTitle)
          }
        }

        session.makeScreenshot.setSuspendPreserveClientId { _, fileName ->
          makeScreenshot(fileName)
        }

        session.showNotification.advise(lifetime) { actionTitle ->
          showNotification("${session.agentInfo.id}: $actionTitle")
        }

        // Initialize loggers
        LogLevelConfigurationManager.getInstance().addCategories(session.traceCategories.map { LogCategory(it, DebugLogLevel.TRACE) } +
                                                                 session.debugCategories.map { LogCategory(it, DebugLogLevel.DEBUG) })

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

  private fun screenshotFile(actionName: String, suffix: String, timeStamp: LocalTime): File {
    var fileName = getArtifactsFileName(actionName, suffix, "png", timeStamp)

    return File(PathManager.getLogPath()).resolve(fileName)
  }

  private suspend fun makeScreenshotOfComponent(screenshotFile: File, component: Component) {
    runLogged("Making screenshot of ${component}") {
      val img = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
      component.printAll(img.createGraphics())
      withContext(Dispatchers.IO + NonCancellable) {
        try {
          ImageIO.write(img, "png", screenshotFile)
          LOG.info("Screenshot is saved at: $screenshotFile")
        }
        catch (t: Throwable) {
          LOG.warn("Exception while writing screenshot image to file", t)
        }
      }
    }
  }

  private suspend fun makeScreenshot(actionName: String): Boolean {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      LOG.warn("Can't make screenshot on application in headless mode.")
      return false
    }

    return runLogged("'$actionName': Making screenshot") {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement() + NonCancellable) { // even if there is a modal window opened
        val timeStamp = LocalTime.now()

        return@withContext try {
          val windows = Window.getWindows().filter { it.height != 0 && it.width != 0 }.filter { it.isShowing }
          windows.forEachIndexed { index, window ->
            val screenshotFile = if (window.isFocusAncestor()) {
              screenshotFile(actionName, "_${index}_focusedWindow", timeStamp)
            }
            else {
              screenshotFile(actionName, "_$index", timeStamp)
            }
            makeScreenshotOfComponent(screenshotFile, window)
          }
          true
        }
        catch (e: Throwable) {
          LOG.warn("Test action 'makeScreenshot' hasn't finished successfully", e)
          false
        }
      }
    }
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
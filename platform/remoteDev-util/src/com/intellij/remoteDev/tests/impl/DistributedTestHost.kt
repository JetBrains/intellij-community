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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.rd.util.setSuspendPreserveClientId
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.*
import com.intellij.remoteDev.tests.modelGenerated.RdAgentType
import com.intellij.remoteDev.tests.modelGenerated.RdProductType
import com.intellij.remoteDev.tests.modelGenerated.RdTestSession
import com.intellij.remoteDev.tests.modelGenerated.distributedTestModel
import com.intellij.ui.AppIcon
import com.intellij.ui.WinFocusStealer
import com.intellij.util.ui.EDT.isCurrentThreadEdt
import com.intellij.util.ui.ImageUtil
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.viewNotNull
import com.jetbrains.rd.util.threading.asRdScheduler
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Frame
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
          val map = testClassObject.initAgent(agentInfo)

          // Play test method
          val testMethod = testClass.getMethod(session.testMethodName)
          testClassObject.performInit(testMethod)
          testMethod.invoke(testClassObject)

          // Advice for processing events
          session.runNextAction.setSuspendPreserveClientId { _, actionTitle ->
            val queue = map[actionTitle] ?: error("There is no Action with name '$actionTitle', something went terribly wrong")
            val action = queue.remove()
            val timeout = action.timeout
            val syncBeforeStart = action.syncBeforeStart
            try {
              assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local when test method starts" }

              LOG.info("'$actionTitle': received action execution request")

              return@setSuspendPreserveClientId withContext(action.coroutineContext) {
                if (syncBeforeStart) {
                  // Sync state across all IDE agents to maintain proper order in protocol events
                  // we don't wat to sync state in case of bg task, as it may be launched with blocked UI thread
                  runLogged("'$actionTitle': Sync protocol events before execution") {
                    withTimeout(3.minutes) {
                      DistributedTestBridge.getInstance().syncProtocolEvents()
                    }
                  }
                }
                if (!app.isHeadlessEnvironment && isNotRdHost && (action.requestFocusBeforeStart ?: isCurrentThreadEdt())) {
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
        // actually doesn't really preserve clientId, not really important here
        // https://youtrack.jetbrains.com/issue/RDCT-653/setSuspendPreserveClientId-with-custom-dispatcher-doesnt-preserve-ClientId
        session.isResponding.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, _ ->
          LOG.info("Answering for session is responding...")
          true
        }

        // actually doesn't really preserve clientId, not really important here
        // https://youtrack.jetbrains.com/issue/RDCT-653/setSuspendPreserveClientId-with-custom-dispatcher-doesnt-preserve-ClientId
        session.visibleFrameNames.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, _ ->
          Window.getWindows().filter { it.isShowing }.filterIsInstance<Frame>().map { it.title }.also {
            LOG.info("Visible frame names: ${it.joinToString(", ", "[", "]")}")
          }
        }

        // actually doesn't really preserve clientId, not really important here
        // https://youtrack.jetbrains.com/issue/RDCT-653/setSuspendPreserveClientId-with-custom-dispatcher-doesnt-preserve-ClientId
        session.projectsNames.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, _ ->
          ProjectManagerEx.getOpenProjects().map { it.name }.also {
            LOG.info("Projects: ${it.joinToString(", ", "[", "]")}")
          }
        }

        // causes problems if not scheduled on ui thread
        session.closeProjectIfOpened.setSuspendPreserveClientId { _, _ ->
          runLogged("Close project if it is opened") {
            ProjectManagerEx.getOpenProjects().forEach {
              // (RDCT-960) ModalityState.current() is used here, because
              // both ModalityState.current() and ModalityState.any() allow to start project closing even under modality,
              // but project closing is not allowed under ModalityState.any() (see doc for ModalityState.any())
              withContext(Dispatchers.EDT + ModalityState.current().asContextElement() + NonCancellable) {
                ProjectManagerEx.getInstanceEx().forceCloseProject(it)
              }
            }
            true
          }
        }
        /**
         * Includes closing the project
         */
        session.exitApp.adviseOn(lifetime, Dispatchers.Default.asRdScheduler) {
          lifetime.launch(Dispatchers.EDT + ModalityState.any().asContextElement() + NonCancellable) {
            LOG.info("Exiting the application...")
            app.exit(true, true, false)
          }
        }

        // actually doesn't really preserve clientId, not really important here
        // https://youtrack.jetbrains.com/issue/RDCT-653/setSuspendPreserveClientId-with-custom-dispatcher-doesnt-preserve-ClientId
        session.requestFocus.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, actionTitle ->
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            requestFocus(actionTitle)
          }
        }

        // actually doesn't really preserve clientId, not really important here
        // https://youtrack.jetbrains.com/issue/RDCT-653/setSuspendPreserveClientId-with-custom-dispatcher-doesnt-preserve-ClientId
        session.makeScreenshot.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, fileName ->
          makeScreenshot(fileName)
        }

        // actually doesn't really preserve clientId, not really important here
        // https://youtrack.jetbrains.com/issue/RDCT-653/setSuspendPreserveClientId-with-custom-dispatcher-doesnt-preserve-ClientId
        session.projectsAreInitialised.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, _ ->
          ProjectManagerEx.getOpenProjects().map { it.isInitialized }.all { true }
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
    val projects = ProjectManagerEx.getOpenProjects()

    if (projects.size > 1) {
      LOG.info("'$actionTitle': Can't choose a project to focus. All projects: ${projects.joinToString(", ")}")
      return false
    }

    val currentProject = projects.singleOrNull()
    return if (currentProject == null) {
      requestFocusNoProject(actionTitle)
    }
    else {
      requestFocusWithProject(currentProject, actionTitle)
    }
  }

  private fun requestFocusWithProject(project: Project, actionTitle: String): Boolean {
    val projectIdeFrame = WindowManager.getInstance().getFrame(project)
    if (projectIdeFrame == null) {
      LOG.info("$actionTitle: No frame yet, nothing to focus")
      return false
    }
    else {
      val windowString = "window '${projectIdeFrame.name}'"
      AppIcon.getInstance().requestFocus(projectIdeFrame)
      if (projectIdeFrame.isFocusAncestor()) {
        LOG.info("$actionTitle: Window '$windowString' is already focused")
        return true
      }
      else {
        LOG.info("$actionTitle: Requesting project focus for '$windowString'")
        ProjectUtil.focusProjectWindow(project, true)
        if (!projectIdeFrame.isFocusAncestor()) {
          LOG.error("Failed to request the focus.")
          return false
        }
        return true
      }
    }
  }

  private fun requestFocusNoProject(actionTitle: String): Boolean {
    val visibleWindows = Window.getWindows().filter { it.isShowing }
    if (visibleWindows.size != 1) {
      LOG.info("$actionTitle: There are multiple windows, will focus them all. All windows: ${visibleWindows.joinToString(", ")}")
    }
    visibleWindows.forEach {
      AppIcon.getInstance().requestFocus(it)
    }
    return true
  }

  private fun screenshotFile(actionName: String, suffix: String, timeStamp: LocalTime): File {
    val fileName = getArtifactsFileName(actionName, suffix, "png", timeStamp)

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
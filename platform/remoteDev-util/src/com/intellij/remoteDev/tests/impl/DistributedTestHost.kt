package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.codeWithMe.asContextElement
import com.intellij.codeWithMe.clientId
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.rd.util.adviseSuspendPreserveClientId
import com.intellij.openapi.rd.util.setSuspendPreserveClientId
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.*
import com.intellij.remoteDev.tests.impl.utils.getArtifactsFileName
import com.intellij.remoteDev.tests.impl.utils.runLogged
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
import com.jetbrains.rd.util.threading.coroutines.asCoroutineDispatcher
import com.jetbrains.rd.util.threading.coroutines.launch
import com.jetbrains.rd.util.threading.coroutines.waitFor
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
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.full.createInstance
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@TestOnly
@ApiStatus.Internal
open class DistributedTestHost(coroutineScope: CoroutineScope) {
  companion object {
    // it is easier to sort out logs from just testFramework
    private val LOG
      get() = Logger.getInstance(RdctTestFrameworkLoggerCategory.category + "Host")

    fun getDistributedTestPort(): Int? =
      System.getProperty(AgentConstants.protocolPortPropertyName)?.toIntOrNull()

    /**
     * ID of the plugin which contains test code.
     * Currently, only test code of the client part is put to a separate plugin.
     */
    const val TEST_PLUGIN_ID: String = "com.intellij.tests.plugin"
    const val TEST_PLUGIN_DIRECTORY_NAME: String = "tests-plugin"
  }

  open fun setUpTestLoggingFactory(sessionLifetime: Lifetime, session: RdTestSession) {
    LOG.info("Setting up test logging factory")
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
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
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
        setUpTestLoggingFactory(sessionLifetime, session)
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
          val testPlugin = PluginManagerCore.getPlugin(PluginId.getId(TEST_PLUGIN_ID))
          val classLoader = if (testPlugin != null) {
            LOG.info("Test class will be loaded from '${testPlugin.pluginId}' plugin")
            testPlugin.pluginClassLoader
          }
          else {
            LOG.info("Test class will be loaded by the core classloader.")
            javaClass.classLoader
          }
          val testClass = Class.forName(session.testClassName, true, classLoader)
          val testClassObject = testClass.kotlin.createInstance() as DistributedTestPlayer

          // Tell test we are running it inside an agent
          val agentInfo = AgentInfo(session.agentInfo, session.testClassName, session.testMethodName)
          val (actionsMap, dimensionRequests) = testClassObject.initAgent(agentInfo)

          // Play test method
          val testMethod = testClass.getMethod(session.testMethodName)
          testClassObject.performInit(testMethod)
          testMethod.invoke(testClassObject)

          val agentContext = when (session.agentInfo.agentType) {
            RdAgentType.HOST -> HostAgentContextImpl(session.agentInfo, protocol)
            RdAgentType.CLIENT -> ClientAgentContextImpl(session.agentInfo, protocol)
            RdAgentType.GATEWAY -> GatewayAgentContextImpl(session.agentInfo, protocol)
          }


          suspend fun <T> runNext(
            actionTitle: String,
            timeout: Duration,
            contextGetter: () -> CoroutineContext,
            requestFocusBeforeStart: Boolean?,
            action: suspend () -> T,
          ): T {
            try {
              assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local before test method starts" }
              LOG.info("'$actionTitle': received action execution request")

              val providedContext = contextGetter.invoke()
              val clientId = providedContext.clientId() ?: ClientId.current

              return withContext(providedContext + clientId.asContextElement()) {
                assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when test method starts" }
                if (!app.isHeadlessEnvironment && isNotRdHost && (requestFocusBeforeStart ?: isCurrentThreadEdt())) {
                  requestFocus(actionTitle)
                }

                assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when after request focus" }

                val result = runLogged(actionTitle, timeout) {
                  action()
                }

                // Assert state
                assertLoggerFactory()

                result
              }
            }
            catch (ex: Throwable) {
              LOG.warn("${session.agentInfo.id}: ${actionTitle.let { "'$it' " }}hasn't finished successfully", ex)
              throw ex
            }
          }

          // Advice for processing events
          session.runNextAction.setSuspendPreserveClientId { _, parameters ->
            val actionTitle = parameters.title
            val queue = actionsMap[actionTitle] ?: error("There is no Action with name '$actionTitle', something went terribly wrong")
            val action = queue.remove()

            return@setSuspendPreserveClientId runNext(actionTitle, action.timeout, action.coroutineContextGetter, action.requestFocusBeforeStart) {
              action.action(agentContext, parameters.parameters)
            }
          }


          session.runNextActionGetComponentData.setSuspendPreserveClientId { _, parameters ->
            val actionTitle = parameters.title
            val queue = dimensionRequests[actionTitle]
                        ?: error("There is no Action with name '$actionTitle', something went terribly wrong")
            val action = queue.remove()
            val timeout = action.timeout

            return@setSuspendPreserveClientId runNext(actionTitle, timeout, action.coroutineContextGetter, action.requestFocusBeforeStart) {
              action.action(agentContext, parameters.parameters)
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

        suspend fun waitProjectInitialisedOrDisposed(it: Project) {
          runLogged("Wait project '${it.name}' is initialised or disposed", 10.seconds) {
            while (!it.isInitialized || it.isDisposed) {
              delay(1.seconds)
            }
          }
        }

        suspend fun leaveAllModals(throwErrorIfModal: Boolean) {
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement() + NonCancellable) {
            repeat(10) {
              if (ModalityState.current() == ModalityState.nonModal()) {
                return@withContext
              }
              delay(1.seconds)
            }
            if (throwErrorIfModal) {
              LOG.error("Unexpected modality: " + ModalityState.current())
            }
            LaterInvocator.forceLeaveAllModals()
            repeat(10) {
              if (ModalityState.current() == ModalityState.nonModal()) {
                return@withContext
              }
              delay(1.seconds)
            }
            LOG.error("Failed to close modal dialog: " + ModalityState.current())
          }
        }

        session.forceLeaveAllModals.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, throwErrorIfModal ->
          leaveAllModals(throwErrorIfModal)
        }

        session.closeProjectIfOpened.setSuspendPreserveClientId(handlerScheduler = Dispatchers.Default.asRdScheduler) { _, _ ->
          leaveAllModals(throwErrorIfModal = true)
          ProjectManagerEx.getOpenProjects().forEach { waitProjectInitialisedOrDisposed(it) }
          withContext(Dispatchers.EDT + NonCancellable) {
            writeIntentReadAction {
              ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(checkCanClose = false)
            }
          }
        }
        /**
         * Includes closing the project
         */
        session.exitApp.adviseOn(lifetime, Dispatchers.Default.asRdScheduler) {
          lifetime.launch(Dispatchers.EDT + NonCancellable) {
            writeIntentReadAction {
              LOG.info("Exiting the application...")
              app.exit(/* force = */ false, /* exitConfirmed = */ true, /* restart = */ false)
            }
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

        session.showNotification.adviseSuspendPreserveClientId(lifetime, Dispatchers.Default.asRdScheduler.asCoroutineDispatcher) { notificationText ->
          showNotification(notificationText)
        }

        // Initialize loggers
        LOG.info("Setting up trace categories from session")
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


  private suspend fun requestFocus(actionTitle: String): Boolean {
    LOG.info("$actionTitle: Requesting focus")

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

  private suspend fun requestFocusWithProject(project: Project, actionTitle: String): Boolean {
    val projectIdeFrame = WindowManager.getInstance().getFrame(project)
    if (projectIdeFrame == null) {
      LOG.info("$actionTitle: No frame yet, nothing to focus")
      return false
    }
    else {
      val windowString = "window '${projectIdeFrame.name}'"
      if (projectIdeFrame.isFocused) {
        LOG.info("$actionTitle: Window '$windowString' is already focused")
        return true
      }
      else {
        LOG.info("$actionTitle: Requesting project focus for '$windowString'")
        ProjectUtil.focusProjectWindow(project, true)
        val waitResult = waitFor(timeout = 5.seconds.toJavaDuration()) {
          projectIdeFrame.isFocused
        }
        if (!waitResult) {
          LOG.error("Couldn't wait for focus in project '$windowString'")
        }
        return waitResult
      }
    }
  }

  private suspend fun requestFocusNoProject(actionTitle: String): Boolean {
    val visibleWindows = Window.getWindows().filter { it.isShowing }
    if (visibleWindows.size != 1) {
      LOG.info("$actionTitle: There are multiple windows, will focus them all. All windows: ${visibleWindows.joinToString(", ")}")
    }
    return visibleWindows.map {
      LOG.info("$actionTitle: Focusing window '$it'")
      AppIcon.getInstance().requestFocus(it)
      val waitResult = waitFor(timeout = 5.seconds.toJavaDuration()) {
        it.isFocused
      }
      if (!waitResult) {
        LOG.error("Couldn't wait for focus in project '$it'")
      }
      waitResult
    }.all { it }
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
            val screenshotFile = if (window.isFocused) {
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
package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.codeWithMe.clientId
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.rd.util.adviseSuspend
import com.intellij.openapi.rd.util.setSuspend
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.*
import com.intellij.remoteDev.tests.impl.utils.getArtifactsFileName
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.modelGenerated.*
import com.intellij.ui.AppIcon
import com.intellij.ui.WinFocusStealer
import com.intellij.util.ui.EDT.isCurrentThreadEdt
import com.intellij.util.ui.ImageUtil
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.viewNotNull
import com.jetbrains.rd.util.threading.coroutines.waitFor
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import java.time.LocalTime
import javax.imageio.ImageIO
import javax.swing.JFrame
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.full.createInstance
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Suppress("NonDefaultConstructor")
@TestOnly
@ApiStatus.Internal
open class DistributedTestHost(coroutineScope: CoroutineScope) {
  companion object {
    // it is easier to sort out logs from just testFramework
    private val LOG
      get() = Logger.getInstance(RdctTestFrameworkLoggerCategory.category + "Host")

    fun getDistributedTestPort(): Int? =
      System.getProperty(DistributedTestsAgentConstants.protocolPortPropertyName)?.toIntOrNull()

    val sourcesRootFolder: File by lazy {
      System.getProperty(DistributedTestsAgentConstants.sourcePathProperty, PathManager.getHomePath()).let(::File)
    }

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
      System.getProperty(DistributedTestsAgentConstants.protocolHostPropertyName)?.let {
        LOG.info("${DistributedTestsAgentConstants.protocolHostPropertyName} system property is set=$it, will try to get address from it.")
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
        val coroutineDumperOnTimeout = launch {
          delay(20.seconds)
          LOG.warn("LoadingState.COMPONENTS_LOADED has not occurred in 20 seconds: ${dumpCoroutines()}")
        }
        while (!LoadingState.COMPONENTS_LOADED.isOccurred) {
          delay(10.milliseconds)
        }
        coroutineDumperOnTimeout.cancel()
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

    val wire = SocketWire.Client(lifetime, DistributedTestIdeScheduler, port, DistributedTestsAgentConstants.protocolName, hostAddress)
    val protocol = Protocol(name = DistributedTestsAgentConstants.protocolName,
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
        @OptIn(ExperimentalCoroutinesApi::class)
        val sessionBgtDispatcher = Dispatchers.Default.limitedParallelism(1, "Test session dispatcher: ${session.testClassName}::${session.testMethodName}")

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
          val testPluginId = System.getProperty("distributed.test.module", TEST_PLUGIN_ID)
          val testPlugin = PluginManagerCore.getPluginSet().findEnabledModule(testPluginId)
                           ?: error("Test plugin '$testPluginId' is not found")

          LOG.info("Test class will be loaded from '${testPlugin.pluginId}' plugin")

          val testClass = Class.forName(session.testClassName, true, testPlugin.pluginClassLoader)
          val testClassObject = testClass.kotlin.createInstance() as DistributedTestPlayer

          // Tell test we are running it inside an agent
          val agentInfo = AgentInfo(session.agentInfo, session.testClassName, session.testMethodName)
          val (actionsMap, getComponentDataRequests) = testClassObject.initAgent(agentInfo)

          // Play test method
          val testMethod = testClass.getMethod(session.testMethodName)
          testClassObject.performInit(testMethod)
          testMethod.invoke(testClassObject)

          suspend fun <T> runNext(
            actionTitle: String,
            timeout: Duration,
            coroutineContextGetter: () -> CoroutineContext,
            requestFocusBeforeStart: Boolean?,
            action: suspend AgentContext.() -> T,
          ): T {
            try {
              assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local before test method starts" }
              LOG.info("'$actionTitle': received action execution request")

              val providedCoroutineContext = coroutineContextGetter.invoke()
              val clientId = providedCoroutineContext.clientId() ?: ClientId.current

              return withContext(providedCoroutineContext) {
                assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when test method starts" }
                if (!app.isHeadlessEnvironment && isNotRdHost && (requestFocusBeforeStart ?: isCurrentThreadEdt())) {
                  requestFocus(silent = false)
                }

                assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when after request focus" }

                val agentContext = when (session.agentInfo.agentType) {
                  RdAgentType.HOST -> HostAgentContextImpl(session.agentInfo, protocol, coroutineContext)
                  RdAgentType.CLIENT -> ClientAgentContextImpl(session.agentInfo, protocol, coroutineContext)
                  RdAgentType.GATEWAY -> GatewayAgentContextImpl(session.agentInfo, protocol, coroutineContext)
                }

                val result = runLogged(actionTitle, timeout) {
                   agentContext.action()
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
          session.runNextAction.setSuspend(sessionBgtDispatcher) { _, parameters ->
            val actionTitle = parameters.title
            val queue = actionsMap[actionTitle] ?: error("There is no Action with name '$actionTitle', something went terribly wrong")
            val agentAction = queue.remove()

            return@setSuspend runNext(actionTitle, agentAction.timeout, agentAction.coroutineContextGetter, agentAction.requestFocusBeforeStart) {
              agentAction.action.invoke(this, parameters.parameters)
            }
          }


          session.runNextActionGetComponentData.setSuspend(sessionBgtDispatcher) { _, parameters ->
            val actionTitle = parameters.title
            val queue = getComponentDataRequests[actionTitle]
                        ?: error("There is no Action with name '$actionTitle', something went terribly wrong")
            val agentActionGetComponentData = queue.remove()

            return@setSuspend runNext(actionTitle, agentActionGetComponentData.timeout,
                                      agentActionGetComponentData.coroutineContextGetter, agentActionGetComponentData.requestFocusBeforeStart) {
              agentActionGetComponentData.action(this, parameters.parameters)
            }
          }
        }

        session.isResponding.setSuspend(sessionBgtDispatcher) { _, _ ->
          LOG.info("Answering for session is responding...")
          true
        }

        session.getProductCodeAndVersion.setSuspend(sessionBgtDispatcher) { _, _ ->
          ApplicationInfo.getInstance().build.let {
            RdProductInfo(productCode = it.productCode, productVersion = it.asStringWithoutProductCode())
          }
        }

        session.visibleFrameNames.setSuspend(sessionBgtDispatcher) { _, _ ->
          Window.getWindows().filter { it.isShowing }.filterIsInstance<Frame>().map { it.title }.also {
            LOG.info("Visible frame names: ${it.joinToString(", ", "[", "]")}")
          }
        }

        session.projectsNames.setSuspend(sessionBgtDispatcher) { _, _ ->
          ProjectManagerEx.getOpenProjects().map { it.name }.also {
            LOG.info("Projects: ${it.joinToString(", ", "[", "]")}")
          }
        }

        suspend fun waitProjectInitialisedOrDisposed(project: Project) {
          runLogged("Wait project '${project.name}' is initialised or disposed", 10.seconds) {
            while (!(project.isInitialized || project.isDisposed)) {
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
            LaterInvocator.forceLeaveAllModals("DistributedTestHost - leaveAllModals")
            repeat(10) {
              if (ModalityState.current() == ModalityState.nonModal()) {
                return@withContext
              }
              delay(1.seconds)
            }
            LOG.error("Failed to close modal dialog: " + ModalityState.current())
          }
        }

        session.forceLeaveAllModals.setSuspend(sessionBgtDispatcher) { _, throwErrorIfModal ->
          leaveAllModals(throwErrorIfModal)
        }

        session.closeAllOpenedProjects.setSuspend(sessionBgtDispatcher) { _, _ ->
          try {
            leaveAllModals(throwErrorIfModal = true)

            ProjectManagerEx.getOpenProjects().forEach { waitProjectInitialisedOrDisposed(it) }
            withContext(Dispatchers.EDT + NonCancellable) {
              writeIntentReadAction {
                ProjectManagerEx.getInstanceEx().closeAndDisposeAllProjects(checkCanClose = false)
              }
            }
          }
          catch (ce: CancellationException) {
            throw ce
          }

        }
        /**
         * Includes closing the project
         */
        session.exitApp.adviseSuspend(lifetime, Dispatchers.EDT + NonCancellable) {
          writeIntentReadAction {
            LOG.info("Exiting the application...")
            app.exit(/* force = */ false, /* exitConfirmed = */ true, /* restart = */ false)
          }
        }

        session.requestFocus.setSuspend(Dispatchers.EDT + ModalityState.any().asContextElement()) { _, silent ->
          requestFocus(silent)
        }

        session.makeScreenshot.setSuspend(sessionBgtDispatcher) { _, fileName ->
          makeScreenshot(fileName)
        }

        session.projectsAreInitialised.setSuspend(sessionBgtDispatcher) { _, _ ->
          ProjectManagerEx.getOpenProjects().map { it.isInitialized }.all { true }
        }

        session.showNotification.adviseSuspend(lifetime, Dispatchers.EDT) { notificationText ->
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


  private suspend fun requestFocus(silent: Boolean): Boolean {
    LOG.info("Requesting focus")

    val projects = ProjectManagerEx.getOpenProjects()

    if (projects.size > 1) {
      LOG.info("Can't choose a project to focus. All projects: ${projects.joinToString(", ")}")
      return false
    }

    val currentProject = projects.singleOrNull()
    return if (currentProject == null) {
      requestFocusNoProject(silent)
    }
    else {
      requestFocusWithProjectIfNeeded(currentProject, silent)
    }
  }

  private suspend fun requestFocusWithProjectIfNeeded(project: Project, silent: Boolean): Boolean {
    val projectIdeFrame = WindowManager.getInstance().getFrame(project)
    if (projectIdeFrame == null) {
      LOG.info("No frame yet, nothing to focus")
      return false
    }
    else {
      val frameName = "frame '${projectIdeFrame.name}'"

      return if ((projectIdeFrame.isFocusAncestor() || projectIdeFrame.isFocused) && !SystemInfo.isWindows) {
        LOG.info("Frame '$frameName' is already focused")
        true
      }
      else {
        requestFocusWithProject(projectIdeFrame, project, frameName, silent)
      }
    }
  }

  private suspend fun requestFocusWithProject(projectIdeFrame: JFrame, project: Project, frameName: String, silent: Boolean): Boolean {
    val logPrefix = "Requesting project focus for '$frameName'"
    LOG.info(logPrefix)

    AppIcon.getInstance().requestFocus(projectIdeFrame)
    ProjectUtil.focusProjectWindow(project, stealFocusIfAppInactive = true)

    return waitFor(timeout = 5.seconds.toJavaDuration()) {
      projectIdeFrame.isFocusAncestor() || projectIdeFrame.isFocused
    }.also {
      if (!it && !silent) {
        LOG.error("$logPrefix: Couldn't wait for focus," +
                  "component isFocused=" + projectIdeFrame.isFocused + " isFocusAncestor=" + projectIdeFrame.isFocusAncestor() +
                  "\n" + getFocusStateDescription()
        )
      }
      else {
        LOG.info("$logPrefix is successful: $it")
      }
    }
  }

  private suspend fun requestFocusNoProject(silent: Boolean): Boolean {
    val logPrefix = "Request for focus (no opened project case)"
    LOG.info(logPrefix)

    val visibleWindows = Window.getWindows().filter { it.isShowing }
    if (visibleWindows.size > 1) {
      LOG.info("$logPrefix There are multiple windows, will focus them all. All windows: ${visibleWindows.joinToString(", ")}")
    }
    visibleWindows.forEach {
      AppIcon.getInstance().requestFocus(it)
    }
    return waitFor(timeout = 5.seconds.toJavaDuration()) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner != null
    }.also {
      if (!it && !silent) {
        LOG.error("$logPrefix: Couldn't wait for focus" +
                  "\n" + getFocusStateDescription())
      }
      else {
        LOG.info("$logPrefix is successful: $it")
      }
    }
  }

  private fun getFocusStateDescription(): String {
    val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

    return "Actual focused component: " +
           "\nfocusedWindow is " + keyboardFocusManager.focusedWindow +
           "\nfocusOwner is " + keyboardFocusManager.focusOwner +
           "\nactiveWindow is " + keyboardFocusManager.activeWindow +
           "\npermanentFocusOwner is " + keyboardFocusManager.permanentFocusOwner
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
private fun showNotification(text: String?) {
  if (ApplicationManager.getApplication().isHeadlessEnvironment || text.isNullOrBlank()) {
    return
  }

  Notification("TestFramework", "Test Framework", text, NotificationType.INFORMATION).notify(null)
}
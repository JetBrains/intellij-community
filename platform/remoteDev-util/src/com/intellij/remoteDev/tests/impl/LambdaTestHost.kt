package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.codeWithMe.clientId
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.rd.util.setSuspend
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.*
import com.intellij.remoteDev.tests.impl.utils.SerializedLambdaLoader
import com.intellij.remoteDev.tests.impl.utils.getArtifactsFileName
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdKeyValueEntry
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestSession
import com.intellij.remoteDev.tests.modelGenerated.lambdaTestModel
import com.intellij.ui.AppIcon
import com.intellij.ui.WinFocusStealer
import com.intellij.util.ui.EDT.isCurrentThreadEdt
import com.intellij.util.ui.ImageUtil
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.viewNotNull
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
import java.net.URLClassLoader
import java.time.LocalTime
import javax.imageio.ImageIO
import javax.swing.JFrame
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSubclassOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestOnly
@ApiStatus.Internal
open class LambdaTestHost(coroutineScope: CoroutineScope) {
  companion object {
    // it is easier to sort out logs from just testFramework
    private val LOG
      get() = Logger.getInstance(RdctTestFrameworkLoggerCategory.category + "Host")

    fun getLambdaTestPort(): Int? =
      System.getProperty(LambdaTestsConstants.protocolPortPropertyName)?.toIntOrNull()

    val sourcesRootFolder: File by lazy {
      System.getProperty(LambdaTestsConstants.sourcePathProperty, PathManager.getHomePath()).let(::File)
    }

    /**
     * ID of the plugin which contains test code.
     * Currently, only test code of the client part is put to a separate plugin.
     */
    const val TEST_MODULE_ID_PROPERTY_NAME: String = "lambda.test.module.id"

    // TODO: plugin: PluginModuleDescriptor might be passed as a context parameter and not via constructor
    abstract class NamedLambda<T : LambdaIdeContext>(protected val lambdaIdeContext: T, protected val plugin: PluginModuleDescriptor) {
      fun name(): String = this::class.qualifiedName ?: error("Can't get qualified name of lambda $this")
      abstract suspend fun T.lambda(args: List<LambdaRdKeyValueEntry>): Any?
      suspend fun runLambda(args: List<LambdaRdKeyValueEntry>) {
        with(lambdaIdeContext) {
          lambda(args = args)
        }
      }
    }
  }

  open fun setUpTestLoggingFactory(sessionLifetime: Lifetime, session: LambdaRdTestSession) {
    LOG.info("Setting up test logging factory")
    LogFactoryHandler.bindSession<AgentTestLoggerFactory>(sessionLifetime, session)
  }

  protected open fun assertLoggerFactory() {
    LogFactoryHandler.assertLoggerFactory<AgentTestLoggerFactory>()
  }

  init {
    val hostAddress =
      System.getProperty(LambdaTestsConstants.protocolHostPropertyName)?.let {
        LOG.info("${LambdaTestsConstants.protocolHostPropertyName} system property is set=$it, will try to get address from it.")
        // this won't work when we do custom network setups as the default gateway will be overridden
        // val hostEntries = File("/etc/hosts").readText().lines()
        // val dockerInterfaceEntry = hostEntries.last { it.isNotBlank() }
        // val ipAddress = dockerInterfaceEntry.split("\\s".toRegex()).first()
        //  host.docker.internal is not available on linux yet (20.04+)
        InetAddress.getByName(it)
      } ?: InetAddress.getLoopbackAddress()

    val port = getLambdaTestPort()
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

  private fun findLambdaClasses(lambdaReference: String, testModuleDescriptor: PluginModuleDescriptor, ideContext: LambdaIdeContext): List<NamedLambda<*>> {
    val className = if (lambdaReference.contains(".Companion")) {
      lambdaReference.substringBeforeLast(".").removeSuffix(".Companion")
    }
    else lambdaReference

    val testClass = Class.forName(className, true, testModuleDescriptor.pluginClassLoader).kotlin

    val companionClasses: Collection<KClass<*>> = testClass.companionObject?.nestedClasses ?: listOf()
    val nestedClasses: Collection<KClass<*>> = testClass.nestedClasses

    val namedLambdas = (companionClasses + nestedClasses + testClass)
      .filter { it.isSubclassOf(NamedLambda::class) }
      .mapNotNull {
        runCatching {
          it.constructors.single().call(ideContext, testModuleDescriptor) as NamedLambda<*> //todo maybe we can filter out constuctor in a more clever way
        }.getOrNull()
      }

    LOG.info("Found ${namedLambdas.size} lambda classes: ${namedLambdas.joinToString(", ") { it.name() }}")

    check(namedLambdas.isNotEmpty()) { "Can't find any named lambda in the test class '${testClass.qualifiedName}'" }

    return namedLambdas
  }

  private fun createProtocol(hostAddress: InetAddress, port: Int) {
    enableCoroutineDump()

    // EternalLifetime.createNested() is used intentionally to make sure logger session's lifetime is not terminated before the actual application stop.
    val lifetime = EternalLifetime.createNested()
    val protocolName = LambdaTestsConstants.protocolName
    LOG.info("Creating protocol '$protocolName' ...")

    val wire = SocketWire.Client(lifetime, LambdaTestIdeScheduler, port, protocolName, hostAddress)
    val protocol = Protocol(name = protocolName,
                            serializers = Serializers(),
                            identity = Identities(IdKind.Client),
                            scheduler = LambdaTestIdeScheduler,
                            wire = wire,
                            lifetime = lifetime)
    val model = protocol.lambdaTestModel

    LOG.info("Advise for session. Current state: ${model.session.value}...")
    model.session.viewNotNull(lifetime) { sessionLifetime, session ->

      try {
        @OptIn(ExperimentalCoroutinesApi::class)
        val sessionBgtDispatcher = Dispatchers.Default.limitedParallelism(1, "Lambda test session dispatcher")

        setUpTestLoggingFactory(sessionLifetime, session)
        val app = ApplicationManager.getApplication()

        // Needed to enable proper focus behaviour
        if (SystemInfoRt.isWindows) {
          WinFocusStealer.setFocusStealingEnabled(true)
        }

        val ideContext = when (session.rdIdeType) {
          LambdaRdIdeType.BACKEND -> LambdaBackendContextClass()
          LambdaRdIdeType.FRONTEND -> LambdaFrontendContextClass()
          LambdaRdIdeType.MONOLITH -> LambdaMonolithContextClass()
        }

        val testModuleId = System.getProperty(TEST_MODULE_ID_PROPERTY_NAME)
                           ?: error("Test module ID '$TEST_MODULE_ID_PROPERTY_NAME' is not specified")

        val testModuleDescriptor = PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId(testModuleId, PluginModuleId.JETBRAINS_NAMESPACE))
                                   ?: error("Test plugin with test module '$testModuleId' is not found")

        assert(testModuleDescriptor.pluginClassLoader != null) { "Test plugin with test module '$testModuleId' is not loaded." +
                                                                 "Probably due to missing dependencies, see `com.intellij.ide.plugins.ClassLoaderConfigurator#configureContentModule`." }


        LOG.info("All test code will be loaded using '${testModuleDescriptor.pluginClassLoader}'")

        // Advice for processing events
        session.runLambda.setSuspend(sessionBgtDispatcher) { _, parameters ->
          LOG.info("'${parameters.reference}': received lambda execution request")
          try {
            val lambdaReference = parameters.reference
            val namedLambdas = findLambdaClasses(lambdaReference = lambdaReference, testModuleDescriptor = testModuleDescriptor, ideContext = ideContext)

            val ideAction = namedLambdas.singleOrNull { it.name() == lambdaReference } ?: run {
              val text = "There is no Action with reference '${lambdaReference}', something went terribly wrong, " +
                         "all referenced actions: ${namedLambdas.map { it.name() }}"
              LOG.error(text)
              error(text)
            }

            assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local before test method starts" }
            LOG.info("'$parameters': received action execution request")

            val providedCoroutineContext = Dispatchers.EDT + CoroutineName("Lambda task: ${ideAction.name()}")
            val requestFocusBeforeStart = false
            val clientId = providedCoroutineContext.clientId() ?: ClientId.current

            withContext(providedCoroutineContext) {
              assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when test method starts" }
              if (!app.isHeadlessEnvironment && (requestFocusBeforeStart ?: isCurrentThreadEdt())) {
                requestFocus()
              }

              assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when after request focus" }

              runLogged(parameters.reference, 1.minutes) {
                ideAction.runLambda(parameters.parameters ?: listOf())
              }

              // Assert state
              assertLoggerFactory()
            }
          }
          catch (ex: Throwable) {
            LOG.warn("${session.rdIdeType}: ${parameters.let { "'$it' " }}hasn't finished successfully", ex)
            throw ex
          }
        }

        // Advice for processing events
        session.runSerializedLambda.setSuspend(sessionBgtDispatcher) { _, serializedLambda ->
          try {
            assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local before test method starts" }
            LOG.info("'$serializedLambda': received serialized lambda execution request")

            val providedCoroutineContext = Dispatchers.EDT + CoroutineName("Lambda task: SerializedLambda:${serializedLambda.clazzName}#${serializedLambda.methodName}")
            val requestFocusBeforeStart = false
            val clientId = providedCoroutineContext.clientId() ?: ClientId.current

            withContext(providedCoroutineContext) {
              assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when test method starts" }
              if (!app.isHeadlessEnvironment && (requestFocusBeforeStart ?: isCurrentThreadEdt())) {
                requestFocus()
              }

              assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when after request focus" }

              val urls = serializedLambda.classPath.map { File(it).toURI().toURL() }
              runLogged(serializedLambda.methodName, 1.minutes) {
                URLClassLoader(urls.toTypedArray(), testModuleDescriptor.pluginClassLoader).use {
                  SerializedLambdaLoader().load(serializedLambda.serializedDataBase64, classLoader = it, context = ideContext)
                    .accept(ideContext)
                }
              }

              // Assert state
              assertLoggerFactory()
            }
          }
          catch (ex: Throwable) {
            LOG.warn("${session.rdIdeType}: ${serializedLambda.methodName.let { "'$it' " }}hasn't finished successfully", ex)
            throw ex
          }
          return@setSuspend
        }

        session.isResponding.setSuspend(sessionBgtDispatcher + NonCancellable) { _, _ ->
          LOG.info("Answering for session is responding...")
          true
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
            LaterInvocator.forceLeaveAllModals("LambdaTestHost - leaveAllModals")
            repeat(10) {
              if (ModalityState.current() == ModalityState.nonModal()) {
                return@withContext
              }
              delay(1.seconds)
            }
            LOG.error("Failed to close modal dialog: " + ModalityState.current())
          }
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

        session.requestFocus.setSuspend(Dispatchers.EDT + ModalityState.any().asContextElement()) { _, reportFailures ->
          requestFocus(reportFailures)
        }

        session.makeScreenshot.setSuspend(sessionBgtDispatcher) { _, fileName ->
          makeScreenshot(fileName)
        }

        session.projectsAreInitialised.setSuspend(sessionBgtDispatcher) { _, _ ->
          ProjectManagerEx.getOpenProjects().map { it.isInitialized }.all { true }
        }

        LOG.info("Test session ready!")
        session.ready.value = true
      }
      catch (ex: Throwable) {
        LOG.warn("Test session initialization hasn't finished successfully", ex)
        session.ready.value = false
      }
    }
  }


  private suspend fun requestFocus(reportFailures: Boolean = true): Boolean {
    LOG.info("Requesting focus")

    val projects = ProjectManagerEx.getOpenProjects()

    if (projects.size > 1) {
      LOG.info("Can't choose a project to focus. All projects: ${projects.joinToString(", ")}")
      return false
    }

    val currentProject = projects.singleOrNull()
    return if (currentProject == null) {
      requestFocusNoProject(reportFailures)
    }
    else {
      requestFocusWithProjectIfNeeded(currentProject, reportFailures)
    }
  }

  private suspend fun requestFocusWithProjectIfNeeded(project: Project, reportFailures: Boolean): Boolean {
    val projectIdeFrame = WindowManager.getInstance().getFrame(project)
    if (projectIdeFrame == null) {
      LOG.info("No frame yet, nothing to focus")
      return false
    }
    else {
      val frameName = "frame '${projectIdeFrame.name}'"

      return if ((projectIdeFrame.isFocusAncestor() || projectIdeFrame.isFocused)) {
        LOG.info("Frame '$frameName' is already focused")
        true
      }
      else {
        requestFocusWithProject(projectIdeFrame, project, frameName, reportFailures)
      }
    }
  }

  private suspend fun requestFocusWithProject(projectIdeFrame: JFrame, project: Project, frameName: String, reportFailures: Boolean): Boolean {
    val logPrefix = "Requesting project focus for '$frameName'"
    LOG.info(logPrefix)

    AppIcon.getInstance().requestFocus(projectIdeFrame)
    ProjectUtil.focusProjectWindow(project, stealFocusIfAppInactive = true)

    return withContext(Dispatchers.IO) {
      waitSuspending(logPrefix, timeout = 10.seconds, onFailure = {
        val message = "Couldn't wait for focus of '$frameName'," +
                      "\n" + "component isFocused=" + projectIdeFrame.isFocused + " isFocusAncestor=" + projectIdeFrame.isFocusAncestor() +
                      "\n" + getFocusStateDescription()
        if (reportFailures) {
          LOG.error(message)
        }
        else {
          LOG.info(message)
        }
      }) {
        projectIdeFrame.isFocusAncestor() || projectIdeFrame.isFocused
      }
    }
  }

  private suspend fun requestFocusNoProject(reportFailures: Boolean): Boolean {
    val logPrefix = "Request for focus (no opened project case)"
    LOG.info(logPrefix)

    val visibleWindows = Window.getWindows().filter { it.isShowing }
    if (visibleWindows.size > 1) {
      LOG.info("$logPrefix There are multiple windows, will focus them all. All windows: ${visibleWindows.joinToString(", ")}")
    }
    visibleWindows.forEach {
      AppIcon.getInstance().requestFocus(it)
    }
    return withContext(Dispatchers.IO) {
      waitSuspending(logPrefix, timeout = 10.seconds, onFailure = {
        val message = "Couldn't wait for focus" + "\n" + getFocusStateDescription()
        if (reportFailures) {
          LOG.error(message)
        }
        else {
          LOG.info(message)
        }
      }) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner != null
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
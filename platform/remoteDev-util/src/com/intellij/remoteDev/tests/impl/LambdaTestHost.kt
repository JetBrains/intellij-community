package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.codeWithMe.clientId
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerCore
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
import com.intellij.remoteDev.tests.impl.utils.getArtifactsFileName
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.remoteDev.tests.modelGenerated.lambdaTestModel
import com.intellij.ui.AppIcon
import com.intellij.ui.WinFocusStealer
import com.intellij.util.ui.EDT.isCurrentThreadEdt
import com.intellij.util.ui.ImageUtil
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.util.lifetime.EternalLifetime
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
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
import java.net.InetAddress
import java.time.LocalTime
import javax.imageio.ImageIO
import javax.swing.JFrame
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
    const val TEST_MODULE_ID_PROPERTY_NAME: String = "lambda.test.module"

    abstract class NamedLambda<T : LambdaIdeContext>(private val lambdaIdeContext: T) {
      fun name(): String = this::class.qualifiedName ?: error("Can't get qualified name of lambda")
      abstract suspend fun T.lambda(vararg args: Any?): Any
      suspend fun runLambda(vararg args: Any?) {
        with(lambdaIdeContext) {
          lambda(args = args)
        }
      }
    }

    class ClassLoaderObjectInputStream(
      inputStream: InputStream,
      private val classLoader: ClassLoader,
    ) : ObjectInputStream(inputStream) {

      override fun resolveClass(desc: ObjectStreamClass): Class<*> {
        return Class.forName(desc.name, false, classLoader)
      }
    }

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
    model.session.viewNotNull(lifetime) { _, session ->

      try {
        @OptIn(ExperimentalCoroutinesApi::class)
        val sessionBgtDispatcher = Dispatchers.Default.limitedParallelism(1, "Lambda test session dispatcher")

        val app = ApplicationManager.getApplication()

        // Needed to enable proper focus behaviour
        if (SystemInfoRt.isWindows) {
          WinFocusStealer.setFocusStealingEnabled(true)
        }

        val testModuleId = System.getProperty(TEST_MODULE_ID_PROPERTY_NAME)
                           ?: error("Test module ID '$TEST_MODULE_ID_PROPERTY_NAME' is not specified")

        val testPlugin = PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId(testModuleId, PluginModuleId.JETBRAINS_NAMESPACE))
                         ?: error("Test plugin with test module '$testModuleId' is not found")

        LOG.info("Test class will be loaded from '${testPlugin.pluginId}' plugin")


        val ideContext = when (session.rdIdeInfo.ideType) {
          LambdaRdIdeType.BACKEND -> LambdaBackendContextClass()
          LambdaRdIdeType.FRONTEND -> LambdaFrontendContextClass()
          LambdaRdIdeType.MONOLITH -> LambdaMonolithContextClass()
        }

        // Advice for processing events
        session.runLambda.setSuspend(sessionBgtDispatcher) { _, parameters ->

          val lambdaReference = parameters.reference
          val className = lambdaReference.substringBeforeLast(".").removeSuffix(".Companion")

          val testClass = Class.forName(className, true, testPlugin.pluginClassLoader).kotlin
          val testClassCompanionObject = testClass.companionObject
          val namedLambdas = testClassCompanionObject
                               ?.nestedClasses
                               ?.filter { it.isSubclassOf(NamedLambda::class) }
                               ?.map { it.constructors.single().call(ideContext) as NamedLambda<*> }
                             ?: error("Can't find any named lambda in the test class '${testClass.qualifiedName}'")

          try {
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
                ideAction.runLambda(parameters.parameters)
              }
            }
          }
          catch (ex: Throwable) {
            LOG.warn("${session.rdIdeInfo.id}: ${parameters.let { "'$it' " }}hasn't finished successfully", ex)
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

              val consumer = run {
                val old = Thread.currentThread().contextClassLoader
                Thread.currentThread().contextClassLoader = testPlugin.pluginClassLoader
                try {
                  val bytes = java.util.Base64.getDecoder().decode(serializedLambda.serializedDataBase64)
                  ClassLoaderObjectInputStream(bytes.inputStream(), testPlugin.pluginClassLoader!!).use { it.readObject() } as java.util.function.Consumer<Application>
                }
                finally {
                  Thread.currentThread().contextClassLoader = old
                }
              }

              runLogged(serializedLambda.methodName, 1.minutes) {
                consumer.accept(app)
              }
            }
          }
          catch (ex: Throwable) {
            LOG.warn("${session.rdIdeInfo.id}: ${serializedLambda.methodName.let { "'$it' " }}hasn't finished successfully", ex)
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
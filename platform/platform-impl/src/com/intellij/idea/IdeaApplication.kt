// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.LoadingPhase
import com.intellij.diagnostic.ParallelActivity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.Phases
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.MainRunner
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.SystemDock
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.ui.AppUIUtil
import com.intellij.ui.CustomProtocolHandler
import com.intellij.ui.mac.MacOSApplicationProvider
import com.intellij.ui.mac.touchbar.TouchBarsManager
import com.intellij.util.ArrayUtilRt
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.EventQueue
import java.beans.PropertyChangeListener
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private val SAFE_JAVA_ENV_PARAMETERS = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
private val LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication")
private var ourPerformProjectLoad = true

private fun executeInitAppInEdt(rawArgs: Array<String>,
                                initAppActivity: Activity,
                                pluginDescriptorsFuture: CompletableFuture<List<IdeaPluginDescriptor>>) {
  val args = processProgramArguments(rawArgs)
  val starter = createAppStarter(args, pluginDescriptorsFuture)

  val createAppActivity = initAppActivity.startChild("create app")
  val headless = Main.isHeadless()
  val app = ApplicationImpl(java.lang.Boolean.getBoolean(PluginManagerCore.IDEA_IS_INTERNAL_PROPERTY), false, headless,
                            Main.isCommandLine(), ApplicationManagerEx.IDEA_APPLICATION)
  createAppActivity.end()

  if (!headless) {
    // todo investigate why in test mode dummy icon manager is not suitable
    IconLoader.activate()
    IconLoader.setStrictGlobally(app.isInternal)

    if (SystemInfo.isMac) {
      initAppActivity.runChild("mac app init") {
        MacOSApplicationProvider.initApplication()
      }
    }

    app.executeOnPooledThread {
      AsyncProcessIcon("")
      AsyncProcessIcon.Big("")
      AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
      AnimatedIcon.FS()
      AllIcons.Ide.Shadow.Top.iconHeight
    }
  }

  starter.premain(args)

  val futures = ArrayList<Future<*>>()
  futures.add(registerRegistryAndMessageBusAndComponent(pluginDescriptorsFuture, app))

  if (!headless) {
    if (SystemInfo.isMac) {
      // ensure that TouchBarsManager is loaded before WelcomeFrame/project
      futures.add(ApplicationManager.getApplication().executeOnPooledThread {
        ParallelActivity.PREPARE_APP_INIT.run("mac touchbar") {
          TouchBarsManager.isTouchBarAvailable()
        }
      })
    }
    SplashManager.showLicenseeInfoOnSplash(LOG)
  }

  // this invokeLater() call is needed to place the app starting code on a freshly minted IdeEventQueue instance
  val placeOnEventQueueActivity = initAppActivity.startChild(Phases.PLACE_ON_EVENT_QUEUE)
  EventQueue.invokeLater {
    placeOnEventQueueActivity.end()
    StartupUtil.installExceptionHandler()
    initAppActivity.end()
    val activity = StartUpMeasurer.start(Phases.WAIT_PLUGIN_INIT)
    for (future in futures) {
      future.get()
    }
    activity.end()

    app.load(null, SplashManager.getProgressIndicator())
    if (!headless) {
      addActivateAndWindowsCliListeners(app)
    }
    (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity { starter.main(args) }

    if (PluginManagerCore.isRunningFromSources()) {
      ApplicationManager.getApplication().executeOnPooledThread { AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame()) }
    }
  }
}

private fun registerRegistryAndMessageBusAndComponent(pluginDescriptorsFuture: CompletableFuture<List<IdeaPluginDescriptor>>,
                                                      app: ApplicationImpl): CompletableFuture<Void> {
  return pluginDescriptorsFuture
    .thenCompose { pluginDescriptors ->
      val future = CompletableFuture.runAsync(Runnable {
        ParallelActivity.PREPARE_APP_INIT.run("init system properties") {
          SystemPropertyBean.initSystemProperties()
        }

        ParallelActivity.PREPARE_APP_INIT.run("add registry keys") {
          RegistryKeyBean.addKeysFromPlugins()
        }

        ParallelActivity.PREPARE_APP_INIT.run("add message bus listeners") {
          app.registerMessageBusListeners(pluginDescriptors, false)
        }
      }, AppExecutorUtil.getAppExecutorService())

      ParallelActivity.PREPARE_APP_INIT.run("app component registration") {
        (ApplicationManager.getApplication() as ApplicationImpl).registerComponents(pluginDescriptors)
      }

      future
    }
}

private fun addActivateAndWindowsCliListeners(app: ApplicationImpl) {
  StartupUtil.addExternalInstanceListener { args ->
    val ref = AtomicReference<Future<out CliResult>>()
    app.invokeAndWait {
      LOG.info("ApplicationImpl.externalInstanceListener invocation")
      val realArgs = if (args.isEmpty()) args else args.subList(1, args.size)
      val projectAndFuture = CommandLineProcessor.processExternalCommandLine(realArgs, args.firstOrNull())

      ref.set(projectAndFuture.getSecond())
      val frame = when (val project = projectAndFuture.getFirst()) {
        null -> WindowManager.getInstance().findVisibleFrame()
        else -> WindowManager.getInstance().getIdeFrame(project) as JFrame
      }
      if (frame != null) {
        if (frame is IdeFrame) {
          AppIcon.getInstance().requestFocus(frame as IdeFrame)
        }
        else {
          frame.toFront()
          DialogEarthquakeShaker.shake(frame)
        }
      }
    }

    ref.get()
  }

  MainRunner.LISTENER = WindowsCommandLineListener { currentDirectory, args ->
    val argsList = args.toList()
    LOG.info("Received external Windows command line: current directory $currentDirectory, command line $argsList")
    if (argsList.isEmpty()) return@WindowsCommandLineListener 0
    var state = app.defaultModalityState
    for (starter in ApplicationStarter.EP_NAME.iterable) {
      if (starter.canProcessExternalCommandLine() && argsList[0] == starter.commandName && starter.allowAnyModalityState()) {
        state = app.anyModalityState
      }
    }

    val ref = AtomicReference<Future<out CliResult>>()
    app.invokeAndWait({ ref.set(CommandLineProcessor.processExternalCommandLine(argsList, currentDirectory).getSecond()) }, state)
    CliResult.getOrWrapFailure(ref.get(), 1).returnCode
  }
}

private fun createAppStarter(args: Array<String>, pluginsLoaded: Future<*>): ApplicationStarter {
  LoadingPhase.setCurrentPhase(LoadingPhase.SPLASH)
  StartupUtil.patchSystem(LOG)

  if (args.isEmpty()) {
    return IdeStarter()
  }

  try {
    pluginsLoaded.get()
  }
  catch (e: InterruptedException) {
    throw CompletionException(e)
  }
  catch (e: ExecutionException) {
    throw CompletionException(e)
  }

  val starter = IdeaApplication.findStarter(args[0])
  if (starter != null) {
    if (Main.isHeadless() && !starter.isHeadless) {
      Main.showMessage("Startup Error", "Application cannot start in headless mode", true)
      exitProcess(Main.NO_GRAPHICS)
    }
    return starter
  }
  return IdeStarter()
}

object IdeaApplication {
  @JvmStatic
  fun initApplication(rawArgs: Array<String>) {
    val initAppActivity = MainRunner.startupStart.endAndStart(Phases.INIT_APP)
    val pluginDescriptorsFuture = CompletableFuture<List<IdeaPluginDescriptor>>()
    EventQueue.invokeLater { executeInitAppInEdt(rawArgs, initAppActivity, pluginDescriptorsFuture) }

    val plugins = try {
      PluginManagerCore.getLoadedPlugins()
    }
    catch (e: Throwable) {
      pluginDescriptorsFuture.completeExceptionally(e)
      return
    }

    pluginDescriptorsFuture.complete(plugins)
  }

  @JvmStatic
  fun findStarter(key: String?): ApplicationStarter? {
    for (starter in ApplicationStarter.EP_NAME.getIterable(null)) {
      if (starter == null) {
        break
      }

      if (Comparing.equal(starter.commandName, key)) {
        return starter
      }
    }
    return null
  }

  @JvmStatic
  fun disableProjectLoad() {
    ourPerformProjectLoad = false
  }
}

open class IdeStarter : ApplicationStarter {
  override fun isHeadless() = false

  override fun getCommandName(): String? = null

  override fun canProcessExternalCommandLine() = true

  override fun processExternalCommandLineAsync(args: Array<String>, currentDirectory: String?): Future<out CliResult> {
    LOG.info("Request to open in " + currentDirectory + " with parameters: " + StringUtil.join(args, ","))

    if (args.isNotEmpty()) {
      val filename = args[0]
      val file = if (currentDirectory == null) Paths.get(filename) else Paths.get(currentDirectory, filename)
      if (Files.exists(file)) {
        var line = -1
        if (args.size > 2 && CustomProtocolHandler.LINE_NUMBER_ARG_NAME == args[1]) {
          try {
            line = Integer.parseInt(args[2])
          }
          catch (ex: NumberFormatException) {
            LOG.error("Wrong line number:" + args[2])
          }

        }
        PlatformProjectOpenProcessor.doOpenProject(file, OpenProjectTask(), line)
      }
      return CliResult.error(1, "Can't find file:$file")
    }
    return CliResult.ok()
  }

  private fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
    var project: Project? = null
    if (commandLineArgs.firstOrNull() != null) {
      LOG.info("IdeaApplication.loadProject")
      project = CommandLineProcessor.processExternalCommandLine(commandLineArgs, null).getFirst()
    }
    return project
  }

  override fun main(args: Array<String>) {
    val frameInitActivity = StartUpMeasurer.start(Phases.FRAME_INITIALIZATION)

    // Event queue should not be changed during initialization of application components.
    // It also cannot be changed before initialization of application components because IdeEventQueue uses other
    // application components. So it is proper to perform replacement only here.
    val setWindowManagerActivity = frameInitActivity.startChild("set window manager")
    val app = ApplicationManager.getApplication()
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    IdeEventQueue.getInstance().setWindowManager(windowManager)
    setWindowManagerActivity.end()

    val commandLineArgs = args.toList()

    val appFrameCreatedActivity = frameInitActivity.startChild("call appFrameCreated")
    val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
    lifecyclePublisher.appFrameCreated(commandLineArgs)
    appFrameCreatedActivity.end()

    // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
    val willOpenProject = commandLineArgs.isNotEmpty() || RecentProjectsManager.getInstance().willReopenProjectOnStart()

    // Temporary check until the jre implementation has been checked and bundled
    if (Registry.`is`("ide.popup.enablePopupType")) {
      @Suppress("SpellCheckingInspection")
      System.setProperty("jbre.popupwindow.settype", "true")
    }

    LoadingPhase.setCurrentPhase(LoadingPhase.FRAME_SHOWN)

    if (!willOpenProject || JetBrainsProtocolHandler.getCommand() != null) {
      WelcomeFrame.showNow(SplashManager.getHideTask())
      lifecyclePublisher.welcomeScreenDisplayed()
    }

    frameInitActivity.end()

    app.executeOnPooledThread { LifecycleUsageTriggerCollector.onIdeStart() }

    TransactionGuard.submitTransaction(app, Runnable {
      val projectFromCommandLine = if (ourPerformProjectLoad) loadProjectFromExternalCommandLine(commandLineArgs) else null
      app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(projectFromCommandLine)
      if (projectFromCommandLine == null && !JetBrainsProtocolHandler.appStartedWithCommand()) {
        RecentProjectsManager.getInstance().reopenLastProjectsOnStart()
      }

      EventQueue.invokeLater { PluginManager.reportPluginError() }
    })

    if (!app.isHeadlessEnvironment) {
      postOpenUiTasks(app)
    }
  }

  private fun postOpenUiTasks(app: Application) {
    if (SystemInfo.isMac) {
      ApplicationManager.getApplication().executeOnPooledThread {
        TouchBarsManager.onApplicationInitialized()
        if (TouchBarsManager.isTouchBarAvailable())
          CustomActionsSchema.addSettingsGroup(IdeActions.GROUP_TOUCHBAR, "Touch Bar")
      }
    }

    app.invokeLater {
      val updateSystemDockActivity = StartUpMeasurer.start("system dock menu")
      SystemDock.updateMenu()
      updateSystemDockActivity.end()
    }
    app.invokeLater {
      val generalSettings = GeneralSettings.getInstance()
      generalSettings.addPropertyChangeListener(GeneralSettings.PROP_SUPPORT_SCREEN_READERS, app,
                                                PropertyChangeListener { e -> ScreenReader.setActive(e.newValue as Boolean) })
      ScreenReader.setActive(generalSettings.isSupportScreenReaders)
    }
  }
}

inline fun Activity.runChild(name: String, task: () -> Unit) {
  val activity = startChild(name)
  task()
  activity.end()
}

inline fun ParallelActivity.run(name: String, task: () -> Unit) {
  val activity = start(name)
  task()
  activity.end()
}

/**
 * Method looks for `-Dkey=value` program arguments and stores some of them in system properties.
 * We should use it for a limited number of safe keys.
 * One of them is a list of ids of required plugins
 *
 * @see SAFE_JAVA_ENV_PARAMETERS
 */
@Suppress("SpellCheckingInspection")
private fun processProgramArguments(args: Array<String>): Array<String> {
  val arguments = ArrayList<String>()
  val safeKeys = SAFE_JAVA_ENV_PARAMETERS.toList()
  for (arg in args) {
    if (arg.startsWith("-D")) {
      val keyValue = arg.substring(2).split('=')
      if (keyValue.size == 2 && safeKeys.contains(keyValue[0])) {
        System.setProperty(keyValue[0], keyValue[1])
        continue
      }
    }
    if (SplashManager.NO_SPLASH == arg) {
      continue
    }

    arguments.add(arg)
  }
  return ArrayUtilRt.toStringArray(arguments)
}
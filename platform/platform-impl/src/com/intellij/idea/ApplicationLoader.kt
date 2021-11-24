// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ApplicationLoader")
@file:ApiStatus.Internal
package com.intellij.idea

import com.intellij.BundleBase
import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.plugins.*
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.wm.WindowManager
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.EDT
import net.miginfocom.layout.PlatformDefaults
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import java.util.function.BiFunction
import javax.swing.UIManager
import kotlin.system.exitProcess

private val SAFE_JAVA_ENV_PARAMETERS = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

fun initApplication(rawArgs: List<String>, prepareUiFuture: CompletionStage<*>) {
  val args = processProgramArguments(rawArgs)

  val initAppActivity = StartupUtil.startupStart.endAndStart(Activities.INIT_APP)
  val loadAndInitPluginFutureActivity = initAppActivity.startChild("plugin descriptor init waiting")
  val loadAndInitPluginFuture = PluginManagerCore.initPlugins(StartupUtil::class.java.classLoader)
  loadAndInitPluginFuture.thenRun(loadAndInitPluginFutureActivity::end)

  prepareUiFuture.thenComposeAsync({
    val isInternal = java.lang.Boolean.getBoolean(ApplicationManagerEx.IS_INTERNAL_PROPERTY)
    val app = ApplicationImpl(isInternal, false, Main.isHeadless(), Main.isCommandLine())
    (UIManager.getLookAndFeel() as? DarculaLaf)?.appCreated(app)
     ApplicationImpl.preventAwtAutoShutdown(app)
     if (isInternal) {
      BundleBase.assertOnMissedKeys(true)
    }

    loadAndInitPluginFuture
      .thenAccept { pluginSet ->
        runActivity("app component registration") {
          app.registerComponents(modules = pluginSet.getEnabledModules(),
                                 app = app,
                                 precomputedExtensionModel = null,
                                 listenerCallbacks = null)
        }

        if (args.isEmpty()) {
          startApp(app, IdeStarter(), initAppActivity, pluginSet, args)
        }
        else {
          // `ApplicationStarter` is an extension, so to find a starter, extensions must be registered first
          findCustomAppStarterAndStart(pluginSet, args, app, initAppActivity)
        }

        if (!Main.isHeadless()) {
          ForkJoinPool.commonPool().execute {
            runActivity("icons preloading") {
              if (isInternal) {
                IconLoader.setStrictGlobally(true)
              }

              AsyncProcessIcon("")
              AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
              AnimatedIcon.FS()
            }

            runActivity("migLayout") {
              // IDEA-170295
              PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE)
            }
          }
        }
      }
  }, Executor { if (EDT.isCurrentThreadEdt()) ForkJoinPool.commonPool().execute(it) else it.run() } )
    .exceptionally {
      StartupAbortedException.processException(it)
      null
    }
}

private fun startApp(app: ApplicationImpl,
                     starter: ApplicationStarter,
                     initAppActivity: Activity,
                     pluginSet: PluginSet,
                     args: List<String>) {
    // initSystemProperties or RegistryKeyBean.addKeysFromPlugins maybe not yet performed,
    // but it is OK, because registry is not and should not be used.
    initConfigurationStore(app)
    val preloadSyncServiceFuture = preloadServices(pluginSet.getEnabledModules(), app, activityPrefix = "")

    val placeOnEventQueueActivity = initAppActivity.startChild(Activities.PLACE_ON_EVENT_QUEUE)
    val loadComponentInEdtFuture = CompletableFuture.runAsync({
      placeOnEventQueueActivity.end()

      val indicator = if (SplashManager.SPLASH_WINDOW == null) {
        null
      }
      else object : EmptyProgressIndicator() {
        override fun setFraction(fraction: Double) {
          SplashManager.SPLASH_WINDOW.showProgress(fraction)
        }
      }
      app.loadComponents(indicator)
    }, Executor(app::invokeLater))

  CompletableFuture.allOf(loadComponentInEdtFuture, preloadSyncServiceFuture, StartupUtil.getServerFuture())
    .thenComposeAsync({
      val pool = ForkJoinPool.commonPool()

      val future = CompletableFuture.runAsync({
        initAppActivity.runChild("app initialized callback") {
          ForkJoinTask.invokeAll(callAppInitialized(app))
        }
      }, pool)

      if (!app.isUnitTestMode && !app.isHeadlessEnvironment &&
          java.lang.Boolean.parseBoolean(System.getProperty("enable.activity.preloading", "true"))) {
        pool.execute { executePreloadActivities(app) }
      }

      pool.execute {
        runActivity("create locator file") {
          createAppLocatorFile()
        }
      }

      if (!Main.isLightEdit()) {
        // this functionality should be used only by plugin functionality that is used after start-up
        pool.execute {
          runActivity("system properties setting") {
            SystemPropertyBean.initSystemProperties()
          }
        }
      }

      future
    }, Executor {
      // if `loadComponentInEdtFuture` is completed after `preloadSyncServiceFuture`,
      // then this task will be executed in EDT, so force execution out of EDT
      if (app.isDispatchThread) {
        ForkJoinPool.commonPool().execute(it)
      }
      else {
        it.run()
      }
    })
    .thenRun {
      addActivateAndWindowsCliListeners()
      initAppActivity.end()

      PluginManagerMain.checkThirdPartyPluginsAllowed()

      if (starter.requiredModality == ApplicationStarter.NOT_IN_EDT) {
        starter.main(args)
        // no need to use pool once plugins are loaded
        ZipFilePool.POOL = null
      }
      else {
        // backward compatibility
        ApplicationManager.getApplication().invokeLater {
          (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
            starter.main(args)
          }
        }
      }
    }
    .exceptionally {
      StartupAbortedException.processException(it)
      null
    }
}

private fun findCustomAppStarterAndStart(pluginSet: PluginSet,
                                         args: List<String>,
                                         app: ApplicationImpl,
                                         initAppActivity: Activity) {
  val starter = findStarter(args.first())
                ?: if (PlatformUtils.getPlatformPrefix() == "LightEdit") IdeStarter.StandaloneLightEditStarter() else IdeStarter()
  if (Main.isHeadless() && !starter.isHeadless) {
    val commandName = starter.commandName
    val message = IdeBundle.message(
      "application.cannot.start.in.a.headless.mode",
      when {
        starter is IdeStarter -> 0
        commandName != null -> 1
        else -> 2
      },
      commandName,
      starter.javaClass.name,
      if (args.isEmpty()) 0 else 1,
      args.joinToString(" ")
    )
    Main.showMessage(IdeBundle.message("main.startup.error"), message, true)
    exitProcess(Main.NO_GRAPHICS)
  }

  starter.premain(args)
  startApp(app, starter, initAppActivity, pluginSet, args)
}

@VisibleForTesting
internal fun createAppLocatorFile() {
  val locatorFile = Path.of(PathManager.getSystemPath(), ApplicationEx.LOCATOR_FILE_NAME)
  try {
    locatorFile.parent?.createDirectories()
    Files.writeString(locatorFile, PathManager.getHomePath(), StandardCharsets.UTF_8)
  }
  catch (e: IOException) {
    LOG.warn("Can't store a location in '$locatorFile'", e)
  }
}

fun preloadServices(modules: Sequence<IdeaPluginDescriptorImpl>,
                    container: ComponentManagerImpl,
                    activityPrefix: String,
                    onlyIfAwait: Boolean = false): CompletableFuture<Void?> {
  val result = container.preloadServices(modules, activityPrefix, onlyIfAwait)

  fun logError(future: CompletableFuture<Void?>): CompletableFuture<Void?> {
    return future
      .whenComplete { _, error ->
        if (error != null && error !is ProcessCanceledException) {
          StartupAbortedException.processException(error)
        }
      }
  }

  logError(result.first)
  return logError(result.second)
}

private fun addActivateAndWindowsCliListeners() {
  StartupUtil.addExternalInstanceListener { rawArgs ->
    LOG.info("External instance command received")
    val (args, currentDirectory) = if (rawArgs.isEmpty()) emptyList<String>() to null else rawArgs.subList(1, rawArgs.size) to rawArgs[0]
    val result = handleExternalCommand(args, currentDirectory)
    result.future
  }

  StartupUtil.LISTENER = BiFunction { currentDirectory, args ->
    LOG.info("External Windows command received")
    if (args.isEmpty()) return@BiFunction 0
    val result = handleExternalCommand(args.toList(), currentDirectory)
    CliResult.unmap(result.future, Main.ACTIVATE_ERROR).exitCode
  }

  ApplicationManager.getApplication().messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      StartupUtil.addExternalInstanceListener { CliResult.error(Main.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")) }
      StartupUtil.LISTENER = BiFunction { _, _ -> Main.ACTIVATE_DISPOSING }
    }
  })
}

private fun handleExternalCommand(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
  val result = if (args.isNotEmpty() && args[0].contains(URLUtil.SCHEME_SEPARATOR)) {
    CommandLineProcessor.processProtocolCommand(args[0])
    CommandLineProcessorResult(null, CommandLineProcessor.OK_FUTURE)
  }
  else {
    CommandLineProcessor.processExternalCommandLine(args, currentDirectory)
  }
  ApplicationManager.getApplication().invokeLater {
    if (result.hasError) {
      result.showErrorIfFailed()
    }
    else {
      val windowManager = WindowManager.getInstance()
      if (result.project == null) {
        windowManager.findVisibleFrame()?.let { frame ->
          frame.toFront()
          DialogEarthquakeShaker.shake(frame)
        }
      }
      else {
        windowManager.getFrame(result.project)?.let {
          AppIcon.getInstance().requestFocus()
        }
      }
    }
  }
  return result
}

fun findStarter(key: String) = ApplicationStarter.EP_NAME.iterable.find { it == null || it.commandName == key }

fun initConfigurationStore(app: ApplicationImpl) {
  var activity = StartUpMeasurer.startActivity("beforeApplicationLoaded")
  val configPath = PathManager.getConfigDir()
  for (listener in ApplicationLoadListener.EP_NAME.iterable) {
    try {
      (listener ?: break).beforeApplicationLoaded(app, configPath)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  activity = activity.endAndStart("init app store")

  // we set it after beforeApplicationLoaded call, because the app store can depend on stream provider state
  app.stateStore.setPath(configPath)
  StartUpMeasurer.setCurrentState(LoadingState.CONFIGURATION_STORE_INITIALIZED)
  activity.end()
}

/**
 * The method looks for `-Dkey=value` program arguments and stores some of them in system properties.
 * We should use it for a limited number of safe keys; one of them is a list of required plugins.
 *
 * @see SAFE_JAVA_ENV_PARAMETERS
 */
@Suppress("SpellCheckingInspection")
private fun processProgramArguments(args: List<String>): List<String> {
  if (args.isEmpty()) {
    return Collections.emptyList()
  }

  val arguments = mutableListOf<String>()
  for (arg in args) {
    if (arg.startsWith("-D")) {
      val keyValue = arg.substring(2).split('=')
      if (keyValue.size == 2 && SAFE_JAVA_ENV_PARAMETERS.contains(keyValue[0])) {
        System.setProperty(keyValue[0], keyValue[1])
        continue
      }
    }
    if (!CommandLineArgs.isKnownArgument(arg)) {
      arguments.add(arg)
    }
  }
  return arguments
}


fun callAppInitialized(app: ApplicationImpl): List<ForkJoinTask<*>> {
  val extensionArea = app.extensionArea
  val extensionPoint = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = ArrayList<ForkJoinTask<*>>(extensionPoint.size())
  extensionPoint.processImplementations(/* shouldBeSorted = */ false) { supplier, _ ->
    result.add(ForkJoinTask.adapt {
      try {
        supplier.get()?.componentsInitialized()
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    })
  }
  extensionPoint.reset()
  return result
}

private fun checkHeavyProcessRunning() {
  if (HeavyProcessLatch.INSTANCE.isRunning) {
    TimeoutUtil.sleep(1)
  }
}

private fun executePreloadActivity(activity: PreloadingActivity, descriptor: PluginDescriptor?, app: ApplicationImpl) {
  checkHeavyProcessRunning()

  val indicator = AbstractProgressIndicatorBase()
  if (app.isDisposed) {
    return
  }

  val isDebugEnabled = LOG.isDebugEnabled
  ProgressManager.getInstance().executeProcessUnderProgress({
    val measureActivity = if (descriptor == null) {
      null
    }
    else {
      StartUpMeasurer.startActivity(activity.javaClass.name, ActivityCategory.PRELOAD_ACTIVITY, descriptor.pluginId.idString)
    }

    try {
      indicator.start()
      activity.preload(object : AbstractProgressIndicatorBase() {
        override fun checkCanceled() {
          checkHeavyProcessRunning()
          indicator.checkCanceled()
        }

        override fun isCanceled() = indicator.isCanceled || app.isDisposed
      })
      if (isDebugEnabled) {
        LOG.debug("${activity.javaClass.name} finished")
      }
    }
    catch (ignore: ProcessCanceledException) {
      return@executeProcessUnderProgress
    }
    finally {
      measureActivity?.end()
      if (indicator.isRunning) {
        indicator.stop()
      }
    }
  }, indicator)
}

private fun executePreloadActivities(app: ApplicationImpl) {
  val activity = StartUpMeasurer.startActivity("preloading activity executing")
  val list = mutableListOf<Pair<PreloadingActivity, PluginDescriptor>>()
  val extensionPoint = app.extensionArea.getExtensionPoint<PreloadingActivity>("com.intellij.preloadingActivity")
  extensionPoint.processImplementations(/* shouldBeSorted = */ false) { supplier, pluginDescriptor ->
    val preloadingActivity: PreloadingActivity
    try {
      preloadingActivity = supplier.get() ?: return@processImplementations
    }
    catch (e: Throwable) {
      LOG.error(e)
      return@processImplementations
    }
    list.add(Pair(preloadingActivity, pluginDescriptor))
  }
  extensionPoint.reset()

  if (list.isEmpty()) {
    return
  }

  // do not execute as a single long task, make sure that other more important tasks may slip in between
  ForkJoinPool.commonPool().execute(object : Runnable {
    private var index = 0

    override fun run() {
      if (app.isDisposed) {
        return
      }

      val item = list[index++]
      executePreloadActivity(item.first, item.second, app)
      if (index == list.size || app.isDisposed) {
        activity.end()
      }
      else {
        ForkJoinPool.commonPool().execute(this)
      }
    }
  })
}

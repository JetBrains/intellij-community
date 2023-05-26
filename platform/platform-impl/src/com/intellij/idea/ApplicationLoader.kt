// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ApplicationLoader")
@file:Internal
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.idea

import com.intellij.diagnostic.*
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.ide.plugins.PluginSet
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.impl.findByIdOrFromInstance
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.lang.ZipFilePool
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction
import kotlin.system.exitProcess

@Suppress("SSBasedInspection")
private val LOG: Logger
  get() = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

fun initApplication(context: InitAppContext) {
  runBlocking(context.context) {
    val (app, preloadCriticalServicesJob) = context.appDeferred.await()
    initApplicationImpl(args = context.args.filterNot { CommandLineArgs.isKnownArgument(it) },
                        initAppActivity = StartUpMeasurer.appInitPreparationActivity!!.endAndStart("app initialization"),
                        pluginSet = PluginManagerCore.getPluginSet(),
                        app = app as ApplicationImpl,
                        preloadCriticalServicesJob = preloadCriticalServicesJob,
                        asyncScope = this)
  }
}

private suspend fun initApplicationImpl(args: List<String>,
                                        initAppActivity: Activity,
                                        pluginSet: PluginSet,
                                        app: ApplicationImpl,
                                        asyncScope: CoroutineScope,
                                        preloadCriticalServicesJob: Job) {
  val deferredStarter = subtask("app starter creation") {
    createAppStarterAsync(args)
  }

  val appInitializedListeners = subtask("app preloading") {
    preloadCriticalServicesJob.join()

    subtask("app service preloading (sync)") {
      app.preloadServices(modules = pluginSet.getEnabledModules(), activityPrefix = "", syncScope = this, asyncScope = asyncScope)
    }

    val appInitListeners = async(CoroutineName("app init listener preload")) {
      getAppInitializedListeners(app)
    }

    launch {
      initAppActivity.runChild("old component init task creating", app::createInitOldComponentsTask)?.let { loadComponentInEdtTask ->
        val placeOnEventQueueActivity = initAppActivity.startChild("place on event queue")
        withContext(RawSwingDispatcher) {
          placeOnEventQueueActivity.end()
          loadComponentInEdtTask()
        }
      }
      StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_LOADED)
    }

    // doesn't block app start-up
    asyncScope.launch(CoroutineName("post app init tasks")) {
      runPostAppInitTasks(app)
    }

    asyncScope.launch {
      addActivateAndWindowsCliListeners()
    }

    appInitListeners.await()
  }

  subtask("app initialized callback") {
    // An async scope here is intended for FLOW. FLOW!!! DO NOT USE the surrounding main scope.
    callAppInitialized(listeners = appInitializedListeners, asyncScope = app.coroutineScope)
  }

  initAppActivity.end()

  val starter = deferredStarter.await()
  if (starter.requiredModality == ApplicationStarter.NOT_IN_EDT) {
    if (starter is ModernApplicationStarter) {
      starter.start(args)
    }
    else {
      // todo https://youtrack.jetbrains.com/issue/IDEA-298594
      CompletableFuture.runAsync {
        starter.main(args)
      }
    }
  }
  else {
    withContext(Dispatchers.EDT) {
      (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
        starter.main(args)
      }
    }
  }
  // no need to use a pool once started
  ZipFilePool.POOL = null
}

fun getAppInitializedListeners(app: Application): List<ApplicationInitializedListener> {
  val extensionArea = app.extensionArea as ExtensionsAreaImpl
  val point = extensionArea.getExtensionPoint<ApplicationInitializedListener>("com.intellij.applicationInitializedListener")
  val result = point.extensionList
  point.reset()
  return result
}

private fun CoroutineScope.runPostAppInitTasks(app: ApplicationImpl) {
  launch(CoroutineName("create locator file") + Dispatchers.IO) {
    createAppLocatorFile()
  }

  if (!AppMode.isLightEdit()) {
    // this functionality should be used only by plugin functionality that is used after start-up
    launch(CoroutineName("system properties setting")) {
      SystemPropertyBean.initSystemProperties()
    }
  }

  launch {
    PluginManagerMain.checkThirdPartyPluginsAllowed()
  }

  if (app.isHeadlessEnvironment) {
    return
  }

  launch(CoroutineName("icons preloading") + Dispatchers.IO) {
    if (app.isInternal) {
      IconLoader.setStrictGlobally(true)
    }

    AsyncProcessIcon("")
    AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
    AnimatedIcon.FS()
  }

  launch {
    if (!app.isUnitTestMode && System.getProperty("enable.activity.preloading", "true").toBoolean()) {
      val extensionPoint = app.extensionArea.getExtensionPoint<PreloadingActivity>("com.intellij.preloadingActivity")
      ExtensionPointName<PreloadingActivity>("com.intellij.preloadingActivity").processExtensions { preloadingActivity, pluginDescriptor ->
        launch {
          executePreloadActivity(preloadingActivity, pluginDescriptor)
        }
      }
      extensionPoint.reset()
    }
  }
}

// `ApplicationStarter` is an extension, so to find a starter, extensions must be registered first
private fun CoroutineScope.createAppStarterAsync(args: List<String>): Deferred<ApplicationStarter> {
  val first = args.firstOrNull()
  // first argument maybe a project path
  if (first == null) {
    return async { IdeStarter() }
  }
  else if (args.size == 1 && OSAgnosticPathUtil.isAbsolute(first)) {
    return async { createDefaultAppStarter() }
  }

  val starter = findStarter(first) ?: createDefaultAppStarter()
  if (AppMode.isHeadless() && !starter.isHeadless) {
    @Suppress("DEPRECATION") val commandName = starter.commandName
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
    StartupErrorReporter.showMessage(IdeBundle.message("main.startup.error"), message, true)
    exitProcess(AppExitCodes.NO_GRAPHICS)
  }

  // must be executed before container creation
  starter.premain(args)
  return CompletableDeferred(value = starter)
}

private fun createDefaultAppStarter(): ApplicationStarter {
  return if (PlatformUtils.getPlatformPrefix() == "LightEdit") IdeStarter.StandaloneLightEditStarter() else IdeStarter()
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

private fun addActivateAndWindowsCliListeners() {
  addExternalInstanceListener { rawArgs ->
    LOG.info("External instance command received")
    val (args, currentDirectory) = if (rawArgs.isEmpty()) emptyList<String>() to null else rawArgs.subList(1, rawArgs.size) to rawArgs[0]
    @Suppress("DEPRECATION")
    ApplicationManager.getApplication().coroutineScope.async {
      handleExternalCommand(args, currentDirectory).future.await()
    }
  }

  EXTERNAL_LISTENER = BiFunction { currentDirectory, args ->
    LOG.info("External Windows command received")
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(Dispatchers.Default) {
      val result = handleExternalCommand(args.asList(), currentDirectory)
      try {
        result.future.await().exitCode
      }
      catch (e: Exception) {
        AppExitCodes.ACTIVATE_ERROR
      }
    }
  }

  ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      addExternalInstanceListener {
        CompletableDeferred(CliResult(AppExitCodes.ACTIVATE_DISPOSING, IdeBundle.message("activation.shutting.down")))
      }
      EXTERNAL_LISTENER = BiFunction { _, _ -> AppExitCodes.ACTIVATE_DISPOSING }
    }
  })
}

private suspend fun handleExternalCommand(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
  if (args.isNotEmpty() && args[0].contains(URLUtil.SCHEME_SEPARATOR)) {
    val result = CommandLineProcessorResult(project = null, result = CommandLineProcessor.processProtocolCommand(args[0]))
    withContext(Dispatchers.EDT) {
      if (result.hasError) {
        result.showError()
      }
      else {
        CommandLineProcessor.findVisibleFrame()?.let { frame ->
          AppIcon.getInstance().requestFocus(frame)
        }
      }
    }
    return result
  }
  else {
    return CommandLineProcessor.processExternalCommandLine(args, currentDirectory, focusApp = true)
  }
}

fun findStarter(key: String): ApplicationStarter? {
  @Suppress("DEPRECATION")
  return ApplicationStarter.EP_NAME.findByIdOrFromInstance(key) { it.commandName }
}

fun CoroutineScope.callAppInitialized(listeners: List<ApplicationInitializedListener>, asyncScope: CoroutineScope) {
  for (listener in listeners) {
    launch {
      listener.execute(asyncScope)
    }
  }
}

private suspend fun executePreloadActivity(activity: PreloadingActivity, descriptor: PluginDescriptor?) {
  val measureActivity = if (descriptor == null) {
    null
  }
  else {
    StartUpMeasurer.startActivity(activity.javaClass.name, ActivityCategory.PRELOAD_ACTIVITY, descriptor.pluginId.idString)
  }

  try {
    activity.execute()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error("cannot execute preloading activity ${activity.javaClass.name}", e)
  }
  finally {
    measureActivity?.end()
  }
}

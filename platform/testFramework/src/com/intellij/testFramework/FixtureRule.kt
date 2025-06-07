// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.configurationStore.LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.project.TestProjectManager
import com.intellij.project.stateStore
import com.intellij.testFramework.common.runAll
import com.intellij.util.containers.forEachGuaranteed
import com.intellij.util.io.sanitizeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.lang.annotation.Inherited
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

private var sharedModule: Module? = null

open class ApplicationRule : TestRule {
  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  final override fun apply(base: Statement, description: Description): Statement = object : Statement() {
    override fun evaluate() {
      before(description)
      try {
        base.evaluate()
      }
      finally {
        after()
      }
    }
  }

  protected open fun before(description: Description) {
    TestApplicationManager.getInstance()
  }

  protected open fun after() { }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use com.intellij.testFramework.junit5.TestApplication annotation")
open class ApplicationExtension : BeforeAllCallback, AfterAllCallback {
  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    TestApplicationManager.getInstance()
  }

  override fun afterAll(context: ExtensionContext) { }
}

/**
 * Rule should be used only and only if you open projects in a custom way in test cases and cannot use [ProjectRule].
 */
class ProjectTrackingRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement = object : Statement() {
    override fun evaluate() {
      (ProjectManager.getInstance() as TestProjectManager).startTracking().use {
        base.evaluate()
      }
    }
  }
}

private class ProjectObject(
  private val runPostStartUpActivities: Boolean = false,
  private val preloadServices: Boolean = false,
  private val projectDescriptor: LightProjectDescriptor? = null,
) {
  private var sharedProject: ProjectEx? = null
  var testClassName: String? = null
  private var virtualFilePointerTracker: VirtualFilePointerTracker? = null
  private var libraryTracker: LibraryTableTracker? = null
  var projectTracker: AccessToken? = null

  fun createProject(): ProjectEx {
    val projectFile = TemporaryDirectory.generateTemporaryPath("project_${testClassName}${ProjectFileType.DOT_DEFAULT_EXTENSION}")
    val options = createTestOpenProjectOptions(runPostStartUpActivities = runPostStartUpActivities)
      .copy(preloadServices = preloadServices)
      .copy(projectName = testClassName)
    val project = (ProjectManager.getInstance() as TestProjectManager).openProject(projectFile, options) as ProjectEx
    virtualFilePointerTracker = VirtualFilePointerTracker()
    libraryTracker = LibraryTableTracker()
    return project
  }

  fun catchAndRethrow(action: () -> Unit) {
    runAll(
      action,
      { sharedProject?.let { PlatformTestUtil.forceCloseProjectWithoutSaving(it) } },
      { projectTracker?.finish() },
      { virtualFilePointerTracker?.assertPointersAreDisposed() },
      { libraryTracker?.assertDisposed() },
      {
        sharedProject = null
        sharedModule = null
      },
    )
  }

  val projectIfOpened: ProjectEx?
    get() = sharedProject

  val project: ProjectEx
    get() {
      var result = sharedProject
      if (result == null) {
        synchronized(this) {
          result = sharedProject
          if (result == null) {
            result = createProject()
            sharedProject = result
          }
        }
      }
      return result!!
    }

  val module: Module
    get() {
      var result = sharedModule
      if (result == null) {
        runInEdtAndWait {
          (projectDescriptor ?: LightProjectDescriptor()).setUpProject(project, object : LightProjectDescriptor.SetupHandler {
            override fun moduleCreated(module: Module) {
              result = module
              sharedModule = module
            }
          })
        }
      }
      return result!!
    }
}

class ProjectRule(
  private val runPostStartUpActivities: Boolean = false,
  preloadServices: Boolean = false,
  projectDescriptor: LightProjectDescriptor? = null,
) : ApplicationRule() {
  companion object {
    @JvmStatic
    fun withRunningStartUpActivities(): ProjectRule = ProjectRule(runPostStartUpActivities = true)
  }

  private val projectObject = ProjectObject(runPostStartUpActivities, preloadServices, projectDescriptor)

  override fun before(description: Description) {
    super.before(description)

    projectObject.testClassName = sanitizeFileName(description.className.substringAfterLast('.'))
    projectObject.projectTracker = (ProjectManager.getInstance() as TestProjectManager).startTracking()
  }

  override fun after() {
    projectObject.catchAndRethrow {
      super.after()
    }
  }

  val projectIfOpened: ProjectEx?
    get() = projectObject.projectIfOpened
  val project: ProjectEx
    get() = projectObject.project
  val module: Module
    get() = projectObject.module
}

/**
 * Encouraged using on static fields to avoid a project creating for each test.
 * Project created on request, so could be used as a bare (only application).
 */
@Suppress("DEPRECATION")
class ProjectExtension(val runPostStartUpActivities: Boolean = false, val preloadServices: Boolean = false) : ApplicationExtension() {
  private var projectObject: ProjectObject? = null

  override fun beforeAll(context: ExtensionContext) {
    super.beforeAll(context)
    projectObject = ProjectObject(runPostStartUpActivities, preloadServices, null).also {
      it.testClassName = sanitizeFileName(context.testClass.map(Class<*>::getSimpleName).orElse(context.displayName).substringAfterLast('.'))
      it.projectTracker = (ProjectManager.getInstance() as TestProjectManager).startTracking()
    }
  }

  override fun afterAll(context: ExtensionContext) {
    checkNotNull(projectObject).catchAndRethrow {
      super.afterAll(context)
    }
    projectObject = null
  }

  val project: ProjectEx
    get() = checkNotNull(projectObject).project
  val module: Module
    get() = checkNotNull(projectObject).module
}

/**
 * rules: outer, middle, inner
 * out:
 * starting outer rule
 * starting middle rule
 * starting inner rule
 * finished inner rule
 * finished middle rule
 * finished outer rule
 */
class RuleChain(vararg val rules: TestRule) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    var statement = base
    for (i in (rules.size - 1) downTo 0) {
      statement = rules[i].apply(statement, description)
    }
    return statement
  }
}

private fun <T : Annotation> Description.getOwnOrClassAnnotation(annotationClass: Class<T>): T? =
  getAnnotation(annotationClass) ?: testClass?.getAnnotation(annotationClass)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Inherited
annotation class RunsInEdt(val writeIntent: Boolean = true)

class EdtRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    val annotation = description.getOwnOrClassAnnotation(RunsInEdt::class.java)
    return if (annotation == null) base else statement {
      runInEdtAndWait(annotation.writeIntent) { base.evaluate() }
    }
  }
}

class InitInspectionRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement = statement { runInInitMode { base.evaluate() } }
}

inline fun statement(crossinline runnable: () -> Unit): Statement = object : Statement() {
  override fun evaluate() {
    runnable()
  }
}

/**
 * Do not optimize test load speed.
 * @see IProjectStore.setOptimiseTestLoadSpeed
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RunsInActiveStoreMode

class ActiveStoreRule(private val projectRule: ProjectRule) : TestRule {
  override fun apply(base: Statement, description: Description): Statement = when {
    description.getOwnOrClassAnnotation(RunsInActiveStoreMode::class.java) == null -> base
    else -> statement {
      projectRule.project.runInLoadComponentStateMode { base.evaluate() }
    }
  }
}

/**
 * In a test mode component state is not loaded.
 * Project or module store will load the component state if a project/module file exists.
 * So there must be a strong reason to explicitly use this method.
 */
inline fun <T> Project.runInLoadComponentStateMode(task: () -> T): T {
  val store = stateStore
  val isModeDisabled = store.isOptimiseTestLoadSpeed
  if (isModeDisabled) {
    store.isOptimiseTestLoadSpeed = false
  }
  try {
    return task()
  }
  finally {
    if (isModeDisabled) {
      store.isOptimiseTestLoadSpeed = true
    }
  }
}

/**
 * Closes a project after [action].
 */
fun <T> Project.useProject(save: Boolean = false, action: (Project) -> T): T {
  try {
    return action(this)
  }
  finally {
    closeProject(save)
  }
}

/**
 * Closes a project asynchronously after [action].
 */
suspend fun <T> Project.useProjectAsync(save: Boolean = false, action: suspend (Project) -> T): T {
  try {
    return action(this)
  }
  finally {
    closeProjectAsync(save)
  }
}

/**
 * Closes a project asynchronously only if [action] is failed.
 */
suspend fun Project.withProjectAsync(action: suspend (Project) -> Unit): Project {
  try {
    action(this)
  }
  catch (e: Throwable) {
    try {
      closeProjectAsync()
    }
    catch (closeException: Throwable) {
      e.addSuppressed(closeException)
    }
    throw e
  }
  return this
}

suspend fun <R> closeOpenedProjectsIfFailAsync(action: suspend () -> R): R = closeOpenedProjectsIfFailImpl({ closeProjectAsync() }, { action() })

fun <R> closeOpenedProjectsIfFail(action: () -> R): R = closeOpenedProjectsIfFailImpl({ closeProject() }, { action() })

private inline fun <R> closeOpenedProjectsIfFailImpl(closeProject: Project.() -> Unit, action: () -> R): R {
  val projectManager = ProjectManager.getInstance()
  val oldOpenedProjects = projectManager.openProjects.toHashSet()
  try {
    return action()
  }
  catch (ex: Throwable) {
    for (project in projectManager.openProjects) {
      if (project !in oldOpenedProjects) {
        try {
          project.closeProject()
        }
        catch (closeException: Throwable) {
          ex.addSuppressed(closeException)
        }
      }
    }
    throw ex
  }
}

private fun Project.closeProject(save: Boolean = false) {
  invokeAndWaitIfNeeded {
    if (save) {
      saveWorkspaceModel()
    }
    ProjectManagerEx.getInstanceEx().forceCloseProject(project = this, save)
  }
}

suspend fun Project.closeProjectAsync(save: Boolean = false) {
  if (save) {
    saveWorkspaceModel()
  }
  ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project = this, save)
}

suspend fun openProjectAsync(path: Path, vararg activities: ProjectActivity): Project {
  return closeOpenedProjectsIfFailAsync {
    ProjectUtil.openOrImportAsync(path)!!
  }.withProjectAsync { project ->
    for (activity in activities) {
      activity.execute(project)
    }
  }
}
suspend fun openProjectAsync(virtualFile: VirtualFile, vararg activities: ProjectActivity): Project = openProjectAsync(virtualFile.toNioPath(), *activities)

class DisposeNonLightProjectsRule : ExternalResource() {
  override fun after() {
    val projectManager = ProjectManagerEx.getInstanceExIfCreated() ?: return
    projectManager.openProjects.forEachGuaranteed {
      if (!ProjectManagerImpl.isLight(it)) {
        ApplicationManager.getApplication().invokeAndWait {
          projectManager.forceCloseProject(it)
        }
      }
    }
  }
}

class DisposeModulesRule(private val projectRule: ProjectRule) : ExternalResource() {
  override fun after() {
    val project = projectRule.projectIfOpened ?: return
    val moduleManager = ModuleManager.getInstance(project)
    ApplicationManager.getApplication().invokeAndWait {
      moduleManager.modules.forEachGuaranteed {
        if (!it.isDisposed && it !== sharedModule) {
          moduleManager.disposeModule(it)
        }
      }
    }
  }
}

fun createProjectAndUseInLoadComponentStateMode(
  tempDirManager: TemporaryDirectory,
  directoryBased: Boolean = false,
  useDefaultProjectSettings: Boolean = true,
  task: (Project) -> Unit,
) {
  val file = tempDirManager.newPath("test${if (directoryBased) "" else ProjectFileType.DOT_DEFAULT_EXTENSION}", refreshVfs = true)
  val project = ProjectManagerEx.getInstanceEx().openProject(file, createTestOpenProjectOptions().copy(
    isNewProject = true,
    useDefaultProjectAsTemplate = useDefaultProjectSettings,
    beforeInit = { it.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true) }
  ))!!
  project.useProject {
    project.runInLoadComponentStateMode {
      task(project)
    }
  }
}

suspend fun loadAndUseProjectInLoadComponentStateMode(
  tempDirManager: TemporaryDirectory,
  projectCreator: ((VirtualFile) -> Path)? = null,
  task: suspend (Project) -> Unit,
) {
  createOrLoadProject(tempDirManager, projectCreator, directoryBased = false, loadComponentState = true, task = task)
}

fun refreshProjectConfigDir(project: Project) {
  LocalFileSystem.getInstance().findFileByNioFile(project.stateStore.directoryStorePath!!)!!.refresh(false, true)
}

fun <T> runNonUndoableWriteAction(file: VirtualFile, runnable: () -> T): T =
  CommandProcessor.getInstance().withUndoTransparentAction().use {
    @Suppress("ForbiddenInSuspectContextMethod")
    ApplicationManager.getApplication().runWriteAction(Computable {
      val result = runnable()
      val documentReference = DocumentReferenceManager.getInstance().create(file)
      val undoManager = UndoManager.getGlobalInstance() as UndoManagerImpl
      undoManager.nonundoableActionPerformed(documentReference, false)
      result
    })
  }

suspend fun createOrLoadProject(
  tempDirManager: TemporaryDirectory,
  projectCreator: ((VirtualFile) -> Path)? = null,
  directoryBased: Boolean = true,
  loadComponentState: Boolean = false,
  useDefaultProjectSettings: Boolean = true,
  runPostStartUpActivities: Boolean = false,
  task: suspend (Project) -> Unit,
) {
  var options = createTestOpenProjectOptions(runPostStartUpActivities).copy(
    useDefaultProjectAsTemplate = useDefaultProjectSettings,
    isNewProject = projectCreator == null,
  )
  if (loadComponentState) {
    val oldBeforeInit = options.beforeInit
    options = options.copy(beforeInit = {
      oldBeforeInit?.invoke(it)
      it.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true)
    })
  }
  createOrLoadProject(tempDirManager, options, projectCreator, directoryBased, task = task)
}

suspend fun createOrLoadProject(
  tempDirManager: TemporaryDirectory,
  options: OpenProjectTask,
  projectCreator: ((VirtualFile) -> Path)? = null,
  directoryBased: Boolean = true,
  loadComponentState: Boolean = false,
  task: suspend (Project) -> Unit,
) {
  val projectPath = if (projectCreator == null) {
    tempDirManager.newPath("test${if (directoryBased) "" else ProjectFileType.DOT_DEFAULT_EXTENSION}", refreshVfs = false)
  }
  else {
    val dir = tempDirManager.createVirtualDir()
    withContext(Dispatchers.EDT) {
      runNonUndoableWriteAction(dir) {
        projectCreator(dir)
      }
    }
  }
  createOrLoadProject(projectPath, loadComponentState, options, task)
}

private fun Project.saveWorkspaceModel() {
  val workspaceModelCache = WorkspaceModelCache.getInstance(this)
  requireNotNull(workspaceModelCache) {
    "WorkspaceModelCache should be enabled explicitly if you need to save the project in tests."
  }
  workspaceModelCache.saveCacheNow()
}

private suspend fun createOrLoadProject(projectPath: Path, loadComponentState: Boolean, options: OpenProjectTask, task: suspend (Project) -> Unit) {
  ProjectManagerEx.getInstanceEx().openProjectAsync(projectPath, options)!!.useProjectAsync { project ->
    if (loadComponentState) {
      project.runInLoadComponentStateMode {
        task(project)
      }
    }
    else {
      task(project)
    }
  }
}

suspend fun loadProject(projectPath: Path, beforeInit: (Project) -> Unit = {}, task: suspend (Project) -> Unit) {
  val options = createTestOpenProjectOptions().copy(
    beforeInit = {
      it.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true)
      beforeInit(it)
    }
  )
  createOrLoadProject(projectPath, loadComponentState = true, options, task)
}

/**
 * Copy files from [projectPaths] directories to a temp directory, load a project from it and pass it to [checkProject].
 */
suspend fun loadProjectAndCheckResults(
  projectPaths: List<Path>,
  tempDirectory: TemporaryDirectory,
  beforeOpen: Consumer<Project>? = null,
  checkProject: suspend (Project) -> Unit,
) {
  val projectCreator = fun(targetDir: VirtualFile): Path {
    val projectDir = targetDir.toNioPath()
    var projectFileName: String? = null
    for (projectPath in projectPaths) {
      val dir = if (Files.isDirectory(projectPath)) {
        projectPath
      }
      else {
        projectFileName = projectPath.fileName.toString()
        projectPath.parent
      }
      FileUtil.copyDir(dir.toFile(), projectDir.toFile())
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, targetDir)
    return if (projectFileName == null) projectDir else projectDir.resolve(projectFileName)
  }
  val directoryBased = projectPaths.all { Files.isDirectory(it) }
  val options = createTestOpenProjectOptions(beforeOpen = beforeOpen).copy(
    useDefaultProjectAsTemplate = false,
    isNewProject = false,
    beforeInit = { it.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true) }
  )
  createOrLoadProject(tempDirectory, options, projectCreator, directoryBased, loadComponentState = true, task = checkProject)
}

open class DisposableRule : ExternalResource() {
  private var _disposable = lazy { Disposer.newDisposable() }

  val disposable: Disposable
    get() = _disposable.value


  @Suppress("ObjectLiteralToLambda")
  inline fun register(crossinline disposable: () -> Unit) {
    Disposer.register(this.disposable, object : Disposable {
      override fun dispose() {
        disposable()
      }
    })
  }

  public override fun after() {
    if (_disposable.isInitialized()) {
      Disposer.dispose(_disposable.value)
      _disposable = lazy { Disposer.newDisposable() }
    }
  }
}

class SystemPropertyRule(private val name: String, private val value: String) : ExternalResource() {
  override fun apply(base: Statement, description: Description): Statement = object: Statement() {
    override fun evaluate() {
      before()
      try {
        PlatformTestUtil.withSystemProperty<RuntimeException>(name, value) { base.evaluate() }
      }
      finally {
        after()
      }
    }
  }
}

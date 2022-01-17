// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.configurationStore.LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runUndoTransparentWriteAction
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.project.TestProjectManager
import com.intellij.project.stateStore
import com.intellij.util.containers.forEachGuaranteed
import com.intellij.util.io.isDirectory
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.throwIfNotEmpty
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.lang.annotation.Inherited
import java.nio.file.Path

private var sharedModule: Module? = null

open class ApplicationRule : TestRule {
  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  final override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
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
  }

  protected open fun before(description: Description) {
    TestApplicationManager.getInstance()
  }

  protected open fun after() {
  }
}

open class ApplicationExtension : BeforeAllCallback, AfterAllCallback {
  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    TestApplicationManager.getInstance()
  }

  override fun afterAll(context: ExtensionContext) {}
}

/**
 * Rule should be used only and only if you open projects in a custom way in test cases and cannot use [ProjectRule].
 */
class ProjectTrackingRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        (ProjectManager.getInstance() as TestProjectManager).startTracking().use {
          base.evaluate()
        }
      }
    }
  }
}

class ProjectObject(private val runPostStartUpActivities: Boolean = false,
                    private val preloadServices: Boolean = false,
                    private val projectDescriptor: LightProjectDescriptor? = null) {
  internal var sharedProject: ProjectEx? = null
  internal var testClassName: String? = null
  var virtualFilePointerTracker: VirtualFilePointerTracker? = null
  var libraryTracker: LibraryTableTracker? = null
  var projectTracker: AccessToken? = null

  internal fun createProject(): ProjectEx {
    val projectFile = TemporaryDirectory.generateTemporaryPath("project_${testClassName}${ProjectFileType.DOT_DEFAULT_EXTENSION}")
    val options = createTestOpenProjectOptions(runPostStartUpActivities = runPostStartUpActivities).copy(preloadServices = preloadServices)
    val project = (ProjectManager.getInstance() as TestProjectManager).openProject(projectFile, options) as ProjectEx
    virtualFilePointerTracker = VirtualFilePointerTracker()
    libraryTracker = LibraryTableTracker()
    return project
  }

  internal fun catchAndRethrow(l: MutableList<Throwable>) {
    l.catchAndStoreExceptions { sharedProject?.let { PlatformTestUtil.forceCloseProjectWithoutSaving(it) } }
    l.catchAndStoreExceptions { projectTracker?.finish() }
    l.catchAndStoreExceptions { virtualFilePointerTracker?.assertPointersAreDisposed() }
    l.catchAndStoreExceptions { libraryTracker?.assertDisposed() }
    l.catchAndStoreExceptions {
      sharedProject = null
      sharedModule = null
    }
    throwIfNotEmpty(l)
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

class ProjectRule(private val runPostStartUpActivities: Boolean = false,
                  preloadServices: Boolean = false,
                  projectDescriptor: LightProjectDescriptor? = null) : ApplicationRule() {
  companion object {
    @JvmStatic
    fun withoutRunningStartUpActivities() = ProjectRule(runPostStartUpActivities = false)
    @JvmStatic
    fun withRunningStartUpActivities() = ProjectRule(runPostStartUpActivities = true)

    /**
     * Think twice before use. And then do not use. To support old code.
     */
    @ApiStatus.Internal
    fun createStandalone(): ProjectRule {
      val result = ProjectRule()
      result.before(Description.EMPTY)
      return result
    }
  }

  private val projectObject = ProjectObject(runPostStartUpActivities, preloadServices, projectDescriptor)
  
  override fun before(description: Description) {
    super.before(description)

    projectObject.testClassName = sanitizeFileName(description.className.substringAfterLast('.'))
    projectObject.projectTracker = (ProjectManager.getInstance() as TestProjectManager).startTracking()
  }

  override fun after() {
    val l = mutableListOf<Throwable>()
    l.catchAndStoreExceptions { super.after() }
    projectObject.catchAndRethrow(l)
  }

  /**
   * Think twice before use. And then do not use. To support old code.
   */
  @ApiStatus.Internal
  fun close() {
    after()
  }

  val projectIfOpened: ProjectEx?
    get() = projectObject.projectIfOpened
  val project: ProjectEx
    get() = projectObject.project
  val module: Module
    get() = projectObject.module
}

/**
 * Encouraged using on static fields to avoid project creating for each test.
 * Project created on request, so, could be used as a bare (only application).
 */
class ProjectExtension(runPostStartUpActivities: Boolean = false,
                       preloadServices: Boolean = false,
                       projectDescriptor: LightProjectDescriptor? = null) : ApplicationExtension() {
  private val projectObject = ProjectObject(runPostStartUpActivities, preloadServices, projectDescriptor)
  
  override fun beforeAll(context: ExtensionContext) {
    super.beforeAll(context)
    projectObject.testClassName = sanitizeFileName(context.testClass.map { it.simpleName }.orElse(context.displayName).substringAfterLast('.'))
    projectObject.projectTracker = (ProjectManager.getInstance() as TestProjectManager).startTracking()
  }

  override fun afterAll(context: ExtensionContext) {
    val l = mutableListOf<Throwable>()
    l.catchAndStoreExceptions { super.afterAll(context) }
    projectObject.catchAndRethrow(l)
  }

  val projectIfOpened: ProjectEx?
    get() = projectObject.sharedProject
  val project: ProjectEx
    get() = projectObject.project
  val module: Module
    get() = projectObject.module
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

private fun <T : Annotation> Description.getOwnOrClassAnnotation(annotationClass: Class<T>) = getAnnotation(annotationClass) ?: testClass?.getAnnotation(annotationClass)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Inherited
annotation class RunsInEdt

class EdtRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return if (description.getOwnOrClassAnnotation(RunsInEdt::class.java) == null) {
      base
    }
    else {
      statement { runInEdtAndWait { base.evaluate() } }
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
 * Do not optimise test load speed.
 * @see IProjectStore.setOptimiseTestLoadSpeed
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RunsInActiveStoreMode

class ActiveStoreRule(private val projectRule: ProjectRule) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    if (description.getOwnOrClassAnnotation(RunsInActiveStoreMode::class.java) == null) {
      return base
    }
    else {
      return statement {
        projectRule.project.runInLoadComponentStateMode { base.evaluate() }
      }
    }
  }
}

/**
 * In test mode component state is not loaded. Project or module store will load component state if project/module file exists.
 * So must be a strong reason to explicitly use this method.
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

inline fun Project.use(task: (Project) -> Unit) {
  try {
    task(this)
  }
  finally {
    PlatformTestUtil.forceCloseProjectWithoutSaving(this)
  }
}

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

/**
 * Only and only if "before" logic in case of exception doesn't require "after" logic - must be no side effects if "before" finished abnormally.
 * So, should be one task per rule.
 */
class WrapRule(private val before: () -> () -> Unit) : TestRule {
  override fun apply(base: Statement, description: Description): Statement = statement {
    val after = before()
    try {
      base.evaluate()
    }
    finally {
      after()
    }
  }
}

fun createProjectAndUseInLoadComponentStateMode(tempDirManager: TemporaryDirectory,
                                                directoryBased: Boolean = false,
                                                useDefaultProjectSettings: Boolean = true,
                                                task: (Project) -> Unit) {
  val file = tempDirManager.newPath("test${if (directoryBased) "" else ProjectFileType.DOT_DEFAULT_EXTENSION}", refreshVfs = true)
  val project = ProjectManagerEx.getInstanceEx().openProject(file, createTestOpenProjectOptions().copy(
    isNewProject = true,
    useDefaultProjectAsTemplate = useDefaultProjectSettings,
    beforeInit = { it.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true) }
  ))!!
  project.use {
    project.runInLoadComponentStateMode {
      task(project)
    }
  }
}

suspend fun loadAndUseProjectInLoadComponentStateMode(tempDirManager: TemporaryDirectory,
                                                      projectCreator: (suspend (VirtualFile) -> Path)? = null,
                                                      task: suspend (Project) -> Unit) {
  createOrLoadProject(tempDirManager, projectCreator, task = task, directoryBased = false, loadComponentState = true)
}

fun refreshProjectConfigDir(project: Project) {
  LocalFileSystem.getInstance().findFileByNioFile(project.stateStore.directoryStorePath!!)!!.refresh(false, true)
}

suspend fun <T> runNonUndoableWriteAction(file: VirtualFile, runnable: suspend () -> T): T {
  return runUndoTransparentWriteAction {
    val result = runBlocking { runnable() }
    val documentReference = DocumentReferenceManager.getInstance().create(file)
    val undoManager = UndoManager.getGlobalInstance() as UndoManagerImpl
    undoManager.nonundoableActionPerformed(documentReference, false)
    result
  }
}

suspend fun createOrLoadProject(tempDirManager: TemporaryDirectory,
                                projectCreator: (suspend (VirtualFile) -> Path)? = null,
                                directoryBased: Boolean = true,
                                loadComponentState: Boolean = false,
                                useDefaultProjectSettings: Boolean = true,
                                task: suspend (Project) -> Unit) {
  val file = if (projectCreator == null) {
    tempDirManager.newPath("test${if (directoryBased) "" else ProjectFileType.DOT_DEFAULT_EXTENSION}", refreshVfs = false)
  }
  else {
    val dir = tempDirManager.createVirtualDir()
    withContext(AppUIExecutor.onWriteThread().coroutineDispatchingContext()) {
      runNonUndoableWriteAction(dir) {
        projectCreator(dir)
      }
    }
  }

  createOrLoadProject(file, useDefaultProjectSettings, projectCreator == null, loadComponentState, task)
}

private suspend fun createOrLoadProject(projectPath: Path,
                                        useDefaultProjectSettings: Boolean,
                                        isNewProject: Boolean,
                                        loadComponentState: Boolean,
                                        task: suspend (Project) -> Unit) {
  var options = createTestOpenProjectOptions().copy(
    useDefaultProjectAsTemplate = useDefaultProjectSettings,
    isNewProject = isNewProject
  )
  if (loadComponentState) {
    options = options.copy(beforeInit = { it.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true) })
  }

  val project = ProjectManagerEx.getInstanceEx().openProject(projectPath, options)!!
  project.use {
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

suspend fun loadProject(projectPath: Path, task: suspend (Project) -> Unit) {
  createOrLoadProject(projectPath, false, false, true, task)
}

/**
 * Copy files from [projectPaths] directories to a temp directory, load project from it and pass it to [checkProject].
 */
fun loadProjectAndCheckResults(projectPaths: List<Path>, tempDirectory: TemporaryDirectory, checkProject: suspend (Project) -> Unit) {
  @Suppress("RedundantSuspendModifier", "BlockingMethodInNonBlockingContext")
  suspend fun copyProjectFiles(targetDir: VirtualFile): Path {
    val projectDir = VfsUtil.virtualToIoFile(targetDir)
    var projectFileName: String? = null
    for (projectPath in projectPaths) {
      val dir = if (projectPath.isDirectory()) projectPath
      else {
        projectFileName = projectPath.fileName.toString()
        projectPath.parent
      }
      FileUtil.copyDir(dir.toFile(), projectDir)
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, targetDir)
    return if (projectFileName != null) projectDir.toPath().resolve(projectFileName) else projectDir.toPath()
  }
  runBlocking {
    createOrLoadProject(tempDirectory, ::copyProjectFiles, directoryBased = projectPaths.all { it.isDirectory() },
                        loadComponentState = true, useDefaultProjectSettings = false) {
      checkProject(it)
    }
  }
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

class DisposableExtension : DisposableRule(), AfterEachCallback {
  override fun afterEach(context: ExtensionContext?) {
    after()
  }
}

class SystemPropertyRule(private val name: String, private val value: String) : ExternalResource() {
  override fun apply(base: Statement, description: Description): Statement {
    return object: Statement() {
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
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.configurationStore.LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.impl.OpenProjectTask
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
import com.intellij.openapi.project.impl.ProjectManagerExImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.project.TestProjectManager
import com.intellij.project.stateStore
import com.intellij.util.containers.forEachGuaranteed
import com.intellij.util.io.sanitizeFileName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Path

private var sharedModule: Module? = null

open class ApplicationRule : TestRule {
  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  final override fun apply(base: Statement, description: Description): Statement? {
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

/**
 * Rule should be used only and only if you open projects in a custom way in test cases and cannot use [ProjectRule].
 */
class ProjectTrackingRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement? {
    return object : Statement() {
      override fun evaluate() {
        (ProjectManager.getInstance() as TestProjectManager).startTracking().use {
          base.evaluate()
        }
      }
    }
  }
}

/**
 * Encouraged using as a ClassRule to avoid project creating for each test.
 * Project created on request, so, could be used as a bare (only application).
 */
class ProjectRule(private val runPostStartUpActivities: Boolean = true, private val projectDescriptor: LightProjectDescriptor = LightProjectDescriptor()) : ApplicationRule() {
  companion object {
    @JvmStatic
    fun withoutRunningStartUpActivities() = ProjectRule(runPostStartUpActivities = false)

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

  private var sharedProject: ProjectEx? = null
  private var testClassName: String? = null
  var virtualFilePointerTracker: VirtualFilePointerTracker? = null
  var projectTracker: AccessToken? = null

  override fun before(description: Description) {
    super.before(description)

    testClassName = sanitizeFileName(description.className.substringAfterLast('.'))
    projectTracker = (ProjectManager.getInstance() as TestProjectManager).startTracking()
  }

  private fun createProject(): ProjectEx {
    val projectFile = TemporaryDirectory.generateTemporaryPath("project_${testClassName}${ProjectFileType.DOT_DEFAULT_EXTENSION}")
    val options = createTestOpenProjectOptions(runPostStartUpActivities = runPostStartUpActivities)
    val project = (ProjectManager.getInstance() as TestProjectManager).openProject(projectFile, options) as ProjectEx
    virtualFilePointerTracker = VirtualFilePointerTracker()
    return project
  }

  override fun after() {
    val l = mutableListOf<Throwable>()
    l.catchAndStoreExceptions { super.after() }
    l.catchAndStoreExceptions { sharedProject?.let { PlatformTestUtil.forceCloseProjectWithoutSaving(it) } }
    l.catchAndStoreExceptions { projectTracker?.finish() }
    l.catchAndStoreExceptions { virtualFilePointerTracker?.assertPointersAreDisposed() }
    l.catchAndStoreExceptions {
      sharedProject = null
      sharedModule = null
    }
    l.throwIfNotEmpty()
  }

  /**
   * Think twice before use. And then do not use. To support old code.
   */
  @ApiStatus.Internal
  fun close() {
    after()
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
          projectDescriptor.setUpProject(project, object : LightProjectDescriptor.SetupHandler {
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

@JvmOverloads
fun createTestOpenProjectOptions(runPostStartUpActivities: Boolean = true): OpenProjectTask {
  // In tests it is caller responsibility to refresh VFS (because often not only the project file must be refreshed, but the whole dir - so, no need to refresh several times).
  // Also, cleanPersistedContents is called on start test application.
  var task = OpenProjectTask(forceOpenInNewFrame = true,
                             isRefreshVfsNeeded = false,
                             runConversionBeforeOpen = false,
                             runConfigurators = false,
                             showWelcomeScreen = false,
                             useDefaultProjectAsTemplate = false)
  if (!runPostStartUpActivities) {
    task = task.copy(beforeInit = {
      it.putUserData(ProjectManagerExImpl.RUN_START_UP_ACTIVITIES, false)
    })
  }
  return task
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
  LocalFileSystem.getInstance().findFileByPath(project.stateStore.projectConfigDir!!)!!.refresh(false, true)
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
    val dir = tempDirManager.newVirtualDirectory()
    withContext(AppUIExecutor.onWriteThread().coroutineDispatchingContext()) {
      runNonUndoableWriteAction(dir) {
        projectCreator(dir)
      }
    }
  }

  var options = createTestOpenProjectOptions().copy(
    useDefaultProjectAsTemplate = useDefaultProjectSettings,
    isNewProject = projectCreator == null
  )
  if (loadComponentState) {
    options = options.copy(beforeInit = { it.putUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE, true) })
  }

  val project = ProjectManagerEx.getInstanceEx().openProject(file, options)!!
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

class DisposableRule : ExternalResource() {
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

  override fun after() {
    if (_disposable.isInitialized()) {
      Disposer.dispose(_disposable.value)
    }
  }
}

class SystemPropertyRule(private val name: String, private val value: String = "true") : ExternalResource() {
  private var oldValue: String? = null

  public override fun before() {
    oldValue = System.getProperty(name)
    System.setProperty(name, value)
  }

  public override fun after() {
    val oldValue = oldValue
    if (oldValue == null) {
      System.clearProperty(name)
    }
    else {
      System.setProperty(name, oldValue)
    }
  }
}
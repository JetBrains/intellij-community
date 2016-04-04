/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.systemIndependentPath
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

private var sharedModule: Module? = null

open class ApplicationRule : ExternalResource() {
  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  override public final fun before() {
    IdeaTestApplication.getInstance()
    TestRunnerUtil.replaceIdeEventQueueSafely()
    (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()
  }
}

/**
 * Project created on request, so, could be used as a bare (only application).
 */
class ProjectRule() : ApplicationRule() {
  companion object {
    private var sharedProject: ProjectEx? = null
    private val projectOpened = AtomicBoolean()

    private fun createLightProject(): ProjectEx {
      (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()

      val projectFile = generateTemporaryPath("light_temp_shared_project${ProjectFileType.DOT_DEFAULT_EXTENSION}")
      val projectPath = projectFile.systemIndependentPath

      val buffer = ByteArrayOutputStream()
      Throwable(projectPath, null).printStackTrace(PrintStream(buffer))

      val project = PlatformTestCase.createProject(projectPath, "Light project: $buffer") as ProjectEx
      Disposer.register(ApplicationManager.getApplication(), Disposable {
        try {
          disposeProject()
        }
        finally {
          Files.deleteIfExists(projectFile)
        }
      })

      (VirtualFilePointerManager.getInstance() as VirtualFilePointerManagerImpl).storePointers()
      return project
    }

    private fun disposeProject() {
      val project = sharedProject ?: return
      sharedProject = null
      sharedModule = null
      Disposer.dispose(project)
    }
  }

  override public fun after() {
    if (projectOpened.compareAndSet(true, false)) {
      sharedProject?.let { runInEdtAndWait { (ProjectManager.getInstance() as ProjectManagerImpl).closeProject(it, false, false, false) } }
    }
  }

  val projectIfOpened: ProjectEx?
    get() = if (projectOpened.get()) sharedProject else null

  val project: ProjectEx
    get() {
      var result = sharedProject
      if (result == null) {
        synchronized(IdeaTestApplication.getInstance()) {
          result = sharedProject
          if (result == null) {
            result = createLightProject()
            sharedProject = result
          }
        }
      }

      if (projectOpened.compareAndSet(false, true)) {
        runInEdtAndWait { ProjectManagerEx.getInstanceEx().openTestProject(project) }
      }
      return result!!
    }

  val module: Module
    get() {
      var project = project
      var result = sharedModule
      if (result == null) {
        runInEdtAndWait {
          LightProjectDescriptor().setUpProject(project, object : LightProjectDescriptor.SetupHandler {
            override fun moduleCreated(module: Module) {
              result = module
              sharedModule = module
            }

            override fun sourceRootCreated(sourceRoot: VirtualFile) {
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

private inline fun statement(crossinline runnable: () -> Unit) = object : Statement() {
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
    return if (description.getOwnOrClassAnnotation(RunsInActiveStoreMode::class.java) == null) {
      base
    }
    else {
      statement { projectRule.project.runInLoadComponentStateMode { base.evaluate() } }
    }
  }
}

/**
 * In test mode component state is not loaded. Project or module store will load component state if project/module file exists.
 * So must be a strong reason to explicitly use this method.
 */
inline fun <T> Project.runInLoadComponentStateMode(task: () -> T): T {
  val store = stateStore as IProjectStore
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

class DisposeNonLightProjectsRule() : ExternalResource() {
  override fun after() {
    val projectManager = if (ApplicationManager.getApplication().isDisposed) null else ProjectManager.getInstance() as ProjectManagerImpl
    projectManager?.openProjects?.forEachGuaranteed {
      if (!ProjectManagerImpl.isLight(it)) {
        runInEdtAndWait { projectManager.closeProject(it, false, true, false) }
      }
    }
  }
}

class DisposeModulesRule(private val projectRule: ProjectRule) : ExternalResource() {
  override fun after() {
    projectRule.projectIfOpened?.let {
      val moduleManager = ModuleManager.getInstance(it)
      runInEdtAndWait {
        moduleManager.modules.forEachGuaranteed {
          if (!it.isDisposed && it !== sharedModule) {
            moduleManager.disposeModule(it)
          }
        }
      }
    }
  }
}

inline fun <T> Array<out T>.forEachGuaranteed(operation: (T) -> Unit): Unit {
  var errors: MutableList<Throwable>? = null
  for (element in this) {
    try {
      operation(element)
    }
    catch (e: Throwable) {
      if (errors == null) {
        errors = SmartList()
      }
      errors.add(e)
    }
  }
  CompoundRuntimeException.throwIfNotEmpty(errors)
}

/**
 * Only and only if "before" logic in case of exception doesn't require "after" logic - must be no side effects if "before" finished abnormally.
 * So, should be one task per rule.
 */
class WrapRule(private val before: () -> () -> Unit) : TestRule {
  override fun apply(base: Statement, description: Description) = statement {
    val after = before()
    try {
      base.evaluate()
    }
    finally {
      after()
    }
  }
}

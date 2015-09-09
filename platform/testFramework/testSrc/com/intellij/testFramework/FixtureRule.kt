/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project created on request, so, could be used as a bare (only application).
 */
public class ProjectRule() : ExternalResource() {
  companion object {
    init {
      Logger.setFactory(javaClass<TestLoggerFactory>())
    }

    private var sharedProject: ProjectEx? = null
    private var sharedModule: Module? = null
    private val projectOpened = AtomicBoolean()

    private fun createLightProject(): ProjectEx {
      (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()

      val projectFile = generateTemporaryPath("light_temp_shared_project${ProjectFileType.DOT_DEFAULT_EXTENSION}").toFile()

      val buffer = ByteArrayOutputStream()
      java.lang.Throwable(projectFile.path).printStackTrace(PrintStream(buffer))

      val project = PlatformTestCase.createProject(projectFile, "Light project: $buffer") as ProjectEx
      Disposer.register(ApplicationManager.getApplication(), Disposable {
        try {
          disposeProject()
        }
        finally {
          FileUtil.delete(projectFile)
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

  override final fun before() {
    IdeaTestApplication.getInstance()
    TestRunnerUtil.replaceIdeEventQueueSafely()
  }

  override fun after() {
    if (projectOpened.compareAndSet(true, false)) {
      sharedProject?.let { runInEdtAndWait { (ProjectManager.getInstance() as ProjectManagerImpl).closeProject(it, false, false, false) } }
    }
  }

  public val projectIfOpened: ProjectEx?
    get() = if (projectOpened.get()) sharedProject else null

  public val project: ProjectEx
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

  public val module: Module
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

public fun runInEdtAndWait(runnable: () -> Unit) {
  EdtTestUtil.runInEdtAndWait(runnable)
}

public class RuleChain(vararg val rules: TestRule) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    var statement = base
    var errors: MutableList<Throwable>? = null
    for (i in (rules.size() - 1) downTo 0) {
      try {
        statement = rules[i].apply(statement, description)
      }
      catch(e: Throwable) {
        if (errors == null) {
          errors = SmartList<Throwable>()
        }
        errors.add(e)
      }
    }

    CompoundRuntimeException.doThrow(errors)
    return statement
  }
}

private fun <T : Annotation> Description.getOwnOrClassAnnotation(annotationClass: Class<T>) = getAnnotation(annotationClass) ?: getTestClass()?.getAnnotation(annotationClass)

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD, ElementType.TYPE)
annotation public class RunsInEdt

public class EdtRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return if (description.getOwnOrClassAnnotation(javaClass<RunsInEdt>()) == null) {
      base
    }
    else {
      object : Statement() {
        override fun evaluate() {
          runInEdtAndWait { base.evaluate() }
        }
      }
    }
  }
}

/**
 * Do not optimise test load speed.
 * @see ProjectEx.setOptimiseTestLoadSpeed
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD, ElementType.TYPE)
annotation public class RunsInActiveStoreMode

public class ActiveStoreRule(private val projectRule: ProjectRule) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return if (description.getOwnOrClassAnnotation(javaClass<RunsInActiveStoreMode>()) == null) {
      base
    }
    else {
      object : Statement() {
        override fun evaluate() {
          val project = projectRule.project
          val isModeDisabled = project.isOptimiseTestLoadSpeed()
          if (isModeDisabled) {
            project.setOptimiseTestLoadSpeed(false)
          }
          try {
            base.evaluate()
          }
          finally {
            if (isModeDisabled) {
              project.setOptimiseTestLoadSpeed(true)
            }
          }
        }
      }
    }
  }
}

public class DisposeModulesRule(private val projectRule: ProjectRule) : ExternalResource() {
  override fun after() {
    projectRule.projectIfOpened?.let {
      var errors: MutableList<Throwable>? = null
      val moduleManager = ModuleManager.getInstance(it)
      runInEdtAndWait {
        for (module in moduleManager.getModules()) {
          if (module.isDisposed()) {
            continue
          }

          try {
            moduleManager.disposeModule(module)
          }
          catch(e: Throwable) {
            if (errors == null) {
              errors = SmartList()
            }
            errors!!.add(e)
          }
        }
      }
      CompoundRuntimeException.doThrow(errors)
    }
  }
}
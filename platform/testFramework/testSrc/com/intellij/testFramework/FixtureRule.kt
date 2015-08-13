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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.properties.Delegates

/**
 * Project created on request, so, could be used as a bare (only application).
 */
public class ProjectRule() : ExternalResource() {
  companion object {
    init {
      Logger.setFactory(javaClass<TestLoggerFactory>())
    }

    private var sharedProject: ProjectEx? = null
    private val projectOpened = AtomicBoolean()

    private fun createLightProject(): ProjectEx {
      (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()

      val projectFile = File("${FileUtilRt.generateRandomTemporaryPath().path}${ProjectFileType.DOT_DEFAULT_EXTENSION}")

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
      Disposer.dispose(project)
    }
  }

  override final fun before() {
    IdeaTestApplication.getInstance()
    UsefulTestCase.replaceIdeEventQueueSafely()
  }

  override fun after() {
    if (projectOpened.compareAndSet(true, false)) {
      sharedProject?.let { runInEdtAndWait { (ProjectManager.getInstance() as ProjectManagerImpl).closeProject(it, false, false, false) } }
    }
  }

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
        ProjectManagerEx.getInstanceEx().openTestProject(project)
      }
      return result!!
    }
}

public open class FixtureRule() : ExternalResource() {
  companion object {
    init {
      Logger.setFactory(javaClass<TestLoggerFactory>())
    }
  }

  protected var _projectFixture: IdeaProjectTestFixture? = null

  public val projectFixture: IdeaProjectTestFixture
    get() = _projectFixture!!

  open fun createBuilder() = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder()

  override final fun before() {
    val builder = createBuilder()
    if (_projectFixture == null) {
      _projectFixture = builder.getFixture()
    }

    UsefulTestCase.replaceIdeEventQueueSafely()
    runInEdtAndWait { projectFixture.setUp() }
  }

  override final fun after() {
    runInEdtAndWait { projectFixture.tearDown() }
  }
}

public fun FixtureRule(tuner: TestFixtureBuilder<IdeaProjectTestFixture>.() -> Unit): FixtureRule = HeavyFixtureRule(tuner)

private class HeavyFixtureRule(private val tune: TestFixtureBuilder<IdeaProjectTestFixture>.() -> Unit) : FixtureRule() {
  private var name: String by Delegates.notNull()

  override final fun apply(base: Statement, description: Description): Statement {
    name = description.getMethodName()
    return super.apply(base, description)
  }

  override final fun createBuilder(): TestFixtureBuilder<IdeaProjectTestFixture> {
    val builder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
    _projectFixture = builder.getFixture()
    builder.tune()
    return builder
  }
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

// Test only because in production you must use Application.invokeAndWait(Runnable, ModalityState).
// The problem is - Application logs errors, but not throws. But in tests must be thrown.
// In any case name "runInEdtAndWait" is better than "invokeAndWait".
public fun runInEdtAndWait(runnable: () -> Unit) {
  if (SwingUtilities.isEventDispatchThread()) {
    runnable()
  }
  else {
    try {
      SwingUtilities.invokeAndWait(runnable)
    }
    catch (e: InvocationTargetException) {
      throw e.getCause() ?: e
    }
  }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD, ElementType.TYPE)
annotation public class RunsInEdt

public class EdtRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return if ((description.getAnnotation(javaClass<RunsInEdt>()) ?: description.getTestClass()!!.getAnnotation(javaClass<RunsInEdt>())) == null) {
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
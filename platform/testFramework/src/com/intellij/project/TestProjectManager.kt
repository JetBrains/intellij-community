// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.project

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.impl.DummyProject
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.impl.ProjectExImpl
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerExImpl
import com.intellij.project.TestProjectManager.Companion.getCreationPlace
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.LeakHunter
import com.intellij.util.PairProcessor
import com.intellij.util.containers.UnsafeWeakList
import com.intellij.util.ref.GCUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

private const val MAX_LEAKY_PROJECTS = 5
private val LEAK_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(30)
private var CHECK_START = System.currentTimeMillis()
private val LOG_PROJECT_LEAKAGE = System.getProperty("idea.log.leaked.projects.in.tests", "true")!!.toBoolean()

@ApiStatus.Internal
internal class TestProjectManager : ProjectManagerExImpl() {
  companion object {
    @JvmStatic
    fun getInstanceExIfCreated(): TestProjectManager? {
      return ProjectManager.getInstanceIfCreated() as TestProjectManager?
    }

    // can be called without application (e.g. Marketplace Tests)
    fun getTotalCreatedProjectCount(): Int {
      return (ApplicationManager.getApplication()?.getServiceIfCreated(ProjectManager::class.java) as TestProjectManager?)?.totalCreatedProjectCount ?: 0
    }

    @JvmStatic
    fun getCreationPlace(project: Project): String {
      return "$project ${(if (project is ProjectEx) project.creationTrace else null) ?: ""}"
    }
  }

  var totalCreatedProjectCount = 0
    private set

  private val projects = WeakHashMap<Project, String>()

  @Volatile
  private var isTracking = false

  private val trackingProjects = UnsafeWeakList<Project>()

  override fun newProject(projectFile: Path, options: OpenProjectTask): Project? {
    totalCreatedProjectCount++
    checkProjectLeaksInTests()

    val project = super.newProject(projectFile, options)
    if (project != null && LOG_PROJECT_LEAKAGE) {
      projects.put(project, null)
    }
    return project
  }

  override fun handleErrorOnNewProject(t: Throwable) {
    throw t
  }

  override fun openProject(project: Project): Boolean {
    if (project is ProjectExImpl && project.isLight) {
      project.setTemporarilyDisposed(false)
      val isInitialized = StartupManagerEx.getInstanceEx(project).startupActivityPassed()
      if (isInitialized) {
        addToOpened(project)
        // events already fired
        return true
      }
    }
    return super.openProject(project)
  }

  // method is not used and will be deprecated soon but still have to ensure that every created Project instance is tracked
  override fun loadProject(file: Path): Project {
    val project = super.loadProject(file)
    trackProject(project)
    return project
  }

  private fun trackProject(project: @NotNull Project) {
    if (isTracking) {
      synchronized(this) {
        if (isTracking) {
          trackingProjects.add(project)
        }
      }
    }
  }

  override fun instantiateProject(projectStoreBaseDir: Path, options: OpenProjectTask): ProjectImpl {
    val project = super.instantiateProject(projectStoreBaseDir, options)
    trackProject(project)
    return project
  }

  override fun closeProject(project: Project, saveProject: Boolean, dispose: Boolean, checkCanClose: Boolean): Boolean {
    if (isTracking) {
      synchronized(this) {
        if (isTracking) {
          trackingProjects.remove(project)
        }
      }
    }
    val result = super.closeProject(project, saveProject, dispose, checkCanClose)
    val undoManager = UndoManager.getGlobalInstance() as UndoManagerImpl
    // test may use WrapInCommand (it is ok - in this case HeavyPlatformTestCase will call dropHistoryInTests)
    if (!undoManager.isInsideCommand) {
      undoManager.dropHistoryInTests()
    }
    return result
  }

  /**
   * Start tracking of created projects. Call [AccessToken.finish] to stop tracking and assert that no leaked projects.
   */
  @Synchronized
  fun startTracking(): AccessToken {
    if (isTracking) {
      throw IllegalStateException("Tracking is already started")
    }

    return object : AccessToken() {
      override fun finish() {
        synchronized(this@TestProjectManager) {
          isTracking = false
          var error: StringBuilder? = null
          for (project in trackingProjects) {
            if (error == null) {
              error = StringBuilder()
            }
            error.append(project.toString())
            error.append("\nCreation trace: ")
            error.append((project as ProjectEx).creationTrace)
          }
          trackingProjects.clear()
          if (error != null) {
            throw IllegalStateException(error.toString())
          }
        }
      }
    }
  }

  private fun getLeakedProjectCount() = getLeakedProjects().count()

  private fun getLeakedProjects(): Sequence<Project> {
    // process queue
    projects.remove(DummyProject.getInstance())
    return projects.keys.asSequence()
  }

  private fun checkProjectLeaksInTests() {
    if (!LOG_PROJECT_LEAKAGE || getLeakedProjectCount() < MAX_LEAKY_PROJECTS) {
      return
    }

    val currentTime = System.currentTimeMillis()
    if ((currentTime - CHECK_START) < LEAK_CHECK_INTERVAL) {
      // check every N minutes
      return
    }

    var i = 0
    while (i < 3 && getLeakedProjectCount() >= MAX_LEAKY_PROJECTS) {
      GCUtil.tryGcSoftlyReachableObjects()
      i++
    }

    CHECK_START = currentTime
    if (getLeakedProjectCount() >= MAX_LEAKY_PROJECTS) {
      System.gc()
      val copy = getLeakedProjects().toCollection(UnsafeWeakList())
      projects.clear()
      if (copy.iterator().asSequence().count() >= MAX_LEAKY_PROJECTS) {
        reportLeakedProjects(copy)
        throw AssertionError("Too many projects leaked, again.")
      }
    }
  }

  override fun isRunStartUpActivitiesEnabled(project: Project): Boolean {
    val runStartUpActivitiesFlag = project.getUserData(ProjectExImpl.RUN_START_UP_ACTIVITIES)
    return runStartUpActivitiesFlag == null || runStartUpActivitiesFlag
  }
}

private fun reportLeakedProjects(leakedProjects: Iterable<Project>) {
  val hashCodes = IntOpenHashSet()
  for (project in leakedProjects) {
    hashCodes.add(System.identityHashCode(project))
  }
  val dumpPath = HeavyPlatformTestCase.publishHeapDump("leakedProjects")
  val leakers = StringBuilder()
  leakers.append("Too many projects leaked: \n")
  LeakHunter.processLeaks(LeakHunter.allRoots(), ProjectImpl::class.java,
                          Predicate { hashCodes.contains(System.identityHashCode(it)) },
                          PairProcessor { leaked: ProjectImpl?, backLink: Any? ->
                            val hashCode = System.identityHashCode(leaked)
                            leakers.append("Leaked project found:").append(leaked)
                              .append("; hash: ").append(hashCode)
                              .append("; place: ").append(getCreationPlace(leaked!!)).append("\n")
                              .append(backLink).append("\n")
                              .append(";-----\n")
                            hashCodes.remove(hashCode)
                            !hashCodes.isEmpty()
                          })
  leakers.append("\nPlease see '$dumpPath' for a memory dump")
  throw AssertionError(leakers.toString())
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.lang

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import training.dsl.LessonContext
import training.learn.course.KLesson
import training.learn.exceptons.InvalidSdkException
import training.learn.exceptons.NoSdkException
import training.util.OnboardingFeedbackData
import java.io.File
import java.io.FileFilter
import java.nio.file.Path
import java.util.Locale

interface LangSupport {
  /** It should be a language ID */
  val primaryLanguage: String
  val defaultProductName: String?
    get() = null

  /** It is a name for content root for learning files. In most cases it is just a learning project name. */
  val contentRootDirectoryName: String

  val langCourseFeedback: String?
    get() = null

  /** The onboarding leson can set this variable to non-null value to invoke feedback dialog and include corresponding data */
  var onboardingFeedbackData: OnboardingFeedbackData?

  /**
   * Should be true iff this language support will not create its own demo project and
   * will use current user project even for non-scratch lessons.
   */
  val useUserProjects: Boolean get() = false

  /** Relative path inside plugin resources */
  val projectResourcePath: String
    get() = "learnProjects/${primaryLanguage.lowercase(Locale.getDefault())}/$contentRootDirectoryName"

  /** Language can specify default sandbox-like file to be used for lessons with modifications but also with project support */
  val sampleFilePath: String?
    get() = null

  /** Language can specify default scratch file name for scratch lessons */
  val scratchFileName: String
    get() = "Learning"

  /** Language support can add tasks to check SDK configuration  */
  val sdkConfigurationTasks: LessonContext.(lesson: KLesson) -> Unit
    get() = {}

  /** Check that the project has needed SDK configured */
  fun isSdkConfigured(project: Project): Boolean

  companion object {
    const val EP_NAME = "training.ift.language.extension"
  }

  fun installAndOpenLearningProject(contentRoot: Path, projectToClose: Project?, postInitCallback: (learnProject: Project) -> Unit)

  fun openOrImportLearningProject(projectRootDirectory: VirtualFile, openProjectTask: OpenProjectTask): Project

  fun copyLearningProjectFiles(projectDirectory: File, destinationFilter: FileFilter? = null): Boolean

  /**
   * Implement that method to define SDK lookup depending on a given project.
   *
   * @return an SDK instance which (existing or newly created) should be applied to the project given. Return `null`
   * if no SDK is okay for this project. If sdk is not null, we call [applyProjectSdk]
   *
   * @throws NoSdkException in the case no valid SDK is available, yet it's required for the given project
   */
  @Throws(NoSdkException::class)
  @RequiresEdt
  fun getSdkForProject(project: Project, selectedSdk: Sdk?): Sdk?

  /**
   * [sdk] is from [getSdkForProject]
   */
  @RequiresEdt
  fun applyProjectSdk(sdk: Sdk, project: Project)

  fun applyToProjectAfterConfigure(): (Project) -> Unit

  /**
   * <p> Implement that method to require some checks for the SDK in the learning project.
   * The easiest check is to check the presence and SdkType, but other checks like language level
   * might be applied as well.
   *
   * <p> The method should not return anything, but might throw exceptions subclassing InvalidSdkException which
   * will be handled by a UI.
   */
  @Throws(InvalidSdkException::class)
  fun checkSdk(sdk: Sdk?, project: Project)

  fun getProjectFilePath(projectName: String): String

  fun getToolWindowAnchor(): ToolWindowAnchor = ToolWindowAnchor.LEFT

  /** true means block source code modification in demo learning projects (scratches can be modified anyway)*/
  fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean = false

  @RequiresBackgroundThread
  fun cleanupBeforeLessons(project: Project) = Unit

  fun startFromWelcomeFrame(startCallback: (Sdk?) -> Unit) {
    startCallback(null)
  }

  fun getLearningProjectPath(contentRoot: Path): Path {
    return contentRoot
  }

  fun getContentRootPath(projectPath: Path): Path {
    return projectPath
  }

  /**
   * When a project dir is cleared using [training.project.ProjectUtils.restoreProject],
   * these paths along with their descenders are protected.
   *
   * For example: `.venv` in Python shouldn't be deleted as it has SDK.
   */
  @RequiresReadLock
  fun getProtectedDirs(project: Project): Set<Path> = emptySet()
}

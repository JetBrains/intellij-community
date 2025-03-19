// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager.Companion.getInstance
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware.Companion.getExtensions
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.application
import com.intellij.util.containers.ContainerUtil
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.execution.BuildLayoutParameters
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionAware
import org.jetbrains.plugins.gradle.service.execution.LocalBuildLayoutParameters
import org.jetbrains.plugins.gradle.service.execution.LocalGradleExecutionAware
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleJvmLookupProvider
import org.jetbrains.plugins.gradle.util.resolveGradleJvmInfo
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Provides discovery utilities about Gradle build environment/layout based on current system environment and IDE configuration.
 */
open class GradleInstallationManager : Disposable.Default {

  private var myCachedGradleHomeFromPath: Ref<Path>? = null
  private val myBuildLayoutParametersCache: MutableMap<String?, BuildLayoutParameters> = ConcurrentHashMap<String?, BuildLayoutParameters>()

  /**
   * Tries to guess build layout parameters for the Gradle build located at [projectPath].
   * Returns default parameters if [projectPath] is not passed in.
   */
  @ApiStatus.Experimental
  fun guessBuildLayoutParameters(project: Project, projectPath: String?): BuildLayoutParameters {
    val cacheKey = projectPath ?: getDefaultProjectKey(project)
    return myBuildLayoutParametersCache.computeIfAbsent(cacheKey) {
      for (executionAware in getExtensions(GradleConstants.SYSTEM_ID)) {
        if (executionAware !is GradleExecutionAware) {
          continue
        }
        val buildLayoutParameters: BuildLayoutParameters? = if (projectPath == null) {
          executionAware.getDefaultBuildLayoutParameters(project)
        }
        else {
          executionAware.getBuildLayoutParameters(project, Path.of(projectPath))
        }
        if (buildLayoutParameters != null) {
          return@computeIfAbsent buildLayoutParameters
        }
      }
      return@computeIfAbsent if (projectPath != null) {
        LocalGradleExecutionAware().getBuildLayoutParameters(project, Path.of(projectPath))
      }
      else {
        LocalGradleExecutionAware().getDefaultBuildLayoutParameters(project)
      }
    }
  }

  fun getGradleHomePath(project: Project?, linkedProjectPath: String): Path? {
    if (project == null) {
      return null
    }
    val buildLayoutParameters = guessBuildLayoutParameters(project, linkedProjectPath)
    return buildLayoutParameters.gradleHome.maybeGetLocalValue()
  }

  @Deprecated("Use getGradleHomePath(Project, String) instead")
  fun getGradleHome(project: Project?, linkedProjectPath: String): File? {
    return getGradleHomePath(project, linkedProjectPath)?.toFile()
  }

  /**
   * Tries to deduce gradle location from the current environment.
   *
   * @return Gradle home deduced from the current environment (if any); null otherwise.
   */
  fun getAutodetectedGradleHome(project: Project?): Path? {
    val pathValue = getGradleHomeFromPath(project)
    if (pathValue != null) {
      return pathValue
    }
    val envValue = getGradleHomeFromEnvProperty(project)
    if (envValue != null) {
      return envValue
    }
    if (SystemInfo.isMac) {
      return gradleHomeFromBrew
    }
    return null
  }

  /**
   * Tries to suggest a better path for the Gradle home.
   *
   * @return proper in terms of [isGradleSdkHome] path or null if it is impossible to fix the path.
   */
  fun suggestBetterGradleHomePath(project: Project?, path: Path): Path? {
    if (path.startsWith(BREW_GRADLE_LOCATION)) {
      val libexecPath = path.resolve(LIBEXEC)
      if (isGradleSdkHome(project, libexecPath)) {
        return libexecPath
      }
    }
    return null
  }

  open fun getGradleJvmPath(project: Project, linkedProjectPath: String): String? {
    val settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath) ?: return getAvailableJavaHome(project)
    val gradleJvm = settings.gradleJvm
    val sdkLookupProvider = getGradleJvmLookupProvider(project, settings)
    val sdkInfo = runBlockingCancellable { sdkLookupProvider.resolveGradleJvmInfo(project, linkedProjectPath, gradleJvm) }
    if (sdkInfo is SdkLookupProvider.SdkInfo.Resolved) {
      return sdkInfo.homePath
    }
    return null
  }

  /**
   * Tries to discover the Gradle installation path from the configured system path.
   *
   * @return the path to the Gradle directory if it's possible to deduce from the system path; `null` otherwise.
   */
  private fun getGradleHomeFromPath(project: Project?): Path? {
    val ref = myCachedGradleHomeFromPath
    if (ref != null) {
      return ref.get()
    }
    val path = System.getenv("PATH") ?: return null
    for (pathEntry in path.split(File.pathSeparator)) {
      val dir = Path.of(pathEntry)
      if (!dir.isDirectory()) {
        continue
      }
      for (fileName in GRADLE_START_FILE_NAMES) {
        val startFile = dir.resolve(fileName)
        if (startFile.isRegularFile()) {
          val candidate = dir.parent
          if (isGradleSdkHome(project, candidate)) {
            myCachedGradleHomeFromPath = Ref<Path>(candidate)
            return candidate
          }
        }
      }
    }
    return null
  }

  /**
   * Tries to discover gradle installation from the environment.
   *
   * @return the path to the Gradle directory deduced from the environment where the project is located.
   */
  private fun getGradleHomeFromEnvProperty(project: Project?): Path? {
    val path = System.getenv(GRADLE_ENV_PROPERTY_NAME) ?: return null
    val candidate = Path.of(path)
    if (isGradleSdkHome(project, candidate)) {
      return candidate
    }
    return null
  }

  /**
   * Check is the given file is a part of the Gradle installation.
   *
   * @param project current IDE project
   * @param file gradle installation root candidate
   * @return `true` if we consider that the given file actually points to the Gradle installation root; `false` otherwise
   */
  fun isGradleSdkHome(project: Project?, file: Path?): Boolean {
    if (file == null) {
      return false
    }
    var actualProject = project
    if (actualProject == null) {
      val projectManager = ProjectManager.getInstance()
      val openProjects = projectManager.getOpenProjects()
      actualProject = if (openProjects.isNotEmpty()) {
        openProjects[0]
      }
      else {
        projectManager.getDefaultProject()
      }
    }
    for (executionAware in getExtensions(GradleConstants.SYSTEM_ID)) {
      if (executionAware !is GradleExecutionAware) continue
      if (executionAware.isGradleInstallationHomeDir(actualProject, file)) {
        return true
      }
    }
    return false
  }

  fun getClassRoots(project: Project?, rootProjectPath: String?): List<Path>? {
    if (project == null) {
      return null
    }
    if (rootProjectPath == null) {
      for (module in getInstance(project).modules) {
        val path = getInstance(module).getRootProjectPath()
        val result = findGradleSdkClasspath(project, path)
        if (!result.isEmpty()) {
          return result
        }
      }
    }
    else {
      return findGradleSdkClasspath(project, rootProjectPath)
    }
    return null
  }

  private fun findGradleSdkClasspath(project: Project, rootProjectPath: String?): List<Path> {
    if (rootProjectPath == null) {
      return emptyList()
    }
    val gradleHome = getGradleHomePath(project, rootProjectPath) ?: return emptyList()
    if (!gradleHome.isDirectory()) {
      return emptyList()
    }
    val result = ArrayList<Path>()
    val src = gradleHome.resolve("src")
    if (src.isDirectory()) {
      if (src.resolve("org").isDirectory()) {
        addRoots(result, src)
      }
      else {
        src.forEachDirectoryEntry { addRoots(result, it) }
      }
    }
    val libraries = getAllLibraries(gradleHome) ?: return result
    for (file in libraries) {
      if (isGradleBuildClasspathLibrary(file)) {
        ContainerUtil.addIfNotNull<Path>(result, file)
      }
    }
    return result
  }

  @ApiStatus.Internal
  internal class ProjectManagerLayoutParametersCacheCleanupListener : ProjectManagerListener {
    override fun projectClosed(project: Project) {
      getInstance().myBuildLayoutParametersCache.clear()
    }
  }

  @ApiStatus.Internal
  internal class DynamicPluginLayoutParametersCacheCleanupListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      getInstance().myBuildLayoutParametersCache.clear()
    }
  }

  @ApiStatus.Internal
  internal class TaskNotificationLayoutParametersCacheCleanupListener : ExternalSystemTaskNotificationListener {
    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
      getInstance().myBuildLayoutParametersCache.remove(projectPath)
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
      // it is not enough to clean up cache on the start of an external event, because sometimes the changes occur `after` the event finishes.
      // An example of this behavior is the downloading of gradle distribution:
      // we must not rely on the caches that were computed without downloaded distribution.
      if (!(id.projectSystemId === GradleConstants.SYSTEM_ID && id.type == ExternalSystemTaskType.RESOLVE_PROJECT)) {
        return
      }
      val project = id.findProject()
      if (project == null) {
        return
      }
      val installationManager: GradleInstallationManager = getInstance()
      installationManager.myBuildLayoutParametersCache.remove(getDefaultProjectKey(project))
      val settings = GradleSettings.getInstance(project)
      for (linkedSettings in settings.linkedProjectsSettings) {
        val path = linkedSettings.externalProjectPath
        installationManager.myBuildLayoutParametersCache.remove(path)
      }
    }
  }

  companion object {
    @JvmField
    val GRADLE_JAR_FILE_PATTERN: Pattern = Pattern.compile(System.getProperty("gradle.pattern.core.jar", "gradle-(core-)?(\\d.*)\\.jar"))
    val ANY_GRADLE_JAR_FILE_PATTERN: Pattern = Pattern.compile(System.getProperty("gradle.pattern.core.jar", "gradle-(.*)\\.jar"))
    val ANT_JAR_PATTERN: Pattern = Pattern.compile("ant(-(.*))?\\.jar")
    val IVY_JAR_PATTERN: Pattern = Pattern.compile("ivy(-(.*))?\\.jar")

    private val GRADLE_START_FILE_NAMES: Array<String> = System.getProperty("gradle.start.file.names", "gradle:gradle.cmd:gradle.sh")
      .split(":".toRegex())
      .toTypedArray()
    private val GRADLE_ENV_PROPERTY_NAME: @NonNls String? = System.getProperty("gradle.home.env.key", "GRADLE_HOME")
    private val BREW_GRADLE_LOCATION: Path = Paths.get("/usr/local/Cellar/gradle/")
    private const val LIBEXEC = "libexec"

    @JvmStatic
    fun getInstance(): GradleInstallationManager {
      return application.service<GradleInstallationManager>()
    }

    private fun getDefaultProjectKey(project: Project): String {
      return project.getLocationHash()
    }

    @JvmStatic
    @ApiStatus.Experimental
    fun defaultBuildLayoutParameters(project: Project): BuildLayoutParameters {
      return getInstance().guessBuildLayoutParameters(project, null)
    }

    private fun getAllLibraries(gradleHome: Path): List<Path>? {
      if (!gradleHome.isDirectory()) {
        return null
      }
      val result = ArrayList<Path>()
      gradleHome.resolve("lib").forEachDirectoryEntry {
        if (it.fileName.toString().endsWith(".jar")) {
          result.add(it)
        }
      }
      gradleHome.resolve("lib/plugins").forEachDirectoryEntry {
        if (it.fileName.toString().endsWith(".jar")) {
          result.add(it)
        }
      }
      return if (result.isEmpty()) null else result
    }

    @JvmStatic
    fun getGradleVersion(gradleHome: Path?): String? {
      if (gradleHome == null) {
        return null
      }
      val libs = gradleHome.resolve("lib")
      if (!libs.isDirectory()) {
        return null
      }
      try {
        Files.list(libs).use { children ->
          return children.map<String?> { path: Path? ->
            val fileName = path!!.fileName
            if (fileName != null) {
              val matcher = GRADLE_JAR_FILE_PATTERN.matcher(fileName.toString())
              if (matcher.matches()) {
                return@map matcher.group(2)
              }
            }
            null
          }
            .filter { Objects.nonNull(it) }
            .findFirst()
            .orElse(null)
        }
      }
      catch (_: IOException) {
        return null
      }
    }

    @JvmStatic
    @Deprecated("Use {@link GradleInstallationManager#getGradleVersion(Path)} instead.")
    fun getGradleVersion(gradleHome: String?): String? {
      if (gradleHome == null) {
        return null
      }
      return getGradleVersion(gradleHome.toNioPathOrNull())
    }

    private val gradleHomeFromBrew: Path?
      get() {
        try {
          Files.newDirectoryStream(BREW_GRADLE_LOCATION).use { ds ->
            var bestPath: Path? = null
            var highestVersion: Version? = null
            for (path in ds) {
              val fileName = path.fileName.toString()
              try {
                val version = Version.parseVersion(fileName)
                if (version == null) continue
                if (highestVersion == null || version > highestVersion) {
                  highestVersion = version
                  bestPath = path
                }
              }
              catch (_: NumberFormatException) {
              }
            }
            if (bestPath != null) {
              val libexecPath: Path = bestPath.resolve(LIBEXEC)
              if (libexecPath.exists()) {
                return libexecPath
              }
            }
          }
        }
        catch (_: Exception) {
        }
        return null
      }

    private fun isGradleBuildClasspathLibrary(file: Path): Boolean {
      val fileName = file.fileName.toString()
      return ANY_GRADLE_JAR_FILE_PATTERN.matcher(fileName).matches()
             || ANT_JAR_PATTERN.matcher(fileName).matches()
             || IVY_JAR_PATTERN.matcher(fileName).matches()
             || isGroovyJar(fileName)
    }

    private fun addRoots(result: MutableList<Path>, vararg files: Path) {
      for (file in files) {
        if (!file.isDirectory()) {
          continue
        }
        result.add(file)
      }
    }

    private fun isGroovyJar(name: String): Boolean {
      var name = name
      name = StringUtil.toLowerCase(name)
      return name.startsWith("groovy-") && name.endsWith(".jar") && !name.contains("src") && !name.contains("doc")
    }

    /**
     * Allows to execute gradle tasks in non imported gradle project
     *
     * @see [IDEA-199979](https://youtrack.jetbrains.com/issue/IDEA-199979)
     */
    private fun getAvailableJavaHome(project: Project): String? {
      val sdkPair = ExternalSystemJdkUtil.getAvailableJdk(project)
      if (ExternalSystemJdkUtil.isValidJdk(sdkPair.second)) {
        return sdkPair.second!!.getHomePath()
      }
      return null
    }

    @JvmStatic
    fun guessGradleVersion(settings: GradleProjectSettings): GradleVersion? {
      val distributionType = settings.distributionType
      if (distributionType == null) return null
      val buildLayoutParameters: BuildLayoutParameters?
      val project: Project? = findProject(settings)
      if (project == null) {
        val defaultProject = ProjectManager.getInstance().getDefaultProject()
        buildLayoutParameters =
          object : LocalBuildLayoutParameters(defaultProject, settings.externalProjectPath.toNioPathOrNull()) {
            public override fun getGradleProjectSettings(): GradleProjectSettings {
              return settings
            }
          }
      }
      else {
        buildLayoutParameters = getInstance().guessBuildLayoutParameters(project, settings.externalProjectPath)
      }
      return buildLayoutParameters.gradleVersion
    }

    @JvmStatic
    fun parseDistributionVersion(path: String): GradleVersion? {
      var path = path
      path = StringUtil.substringAfterLast(path, "/") ?: return null
      path = StringUtil.substringAfterLast(path, "gradle-") ?: return null

      val i = path.lastIndexOf('-')
      if (i <= 0) {
        return null
      }

      return getGradleVersionSafe(path.substring(0, i))
    }

    @JvmStatic
    fun getGradleVersionSafe(gradleVersion: String): GradleVersion? {
      try {
        return GradleVersion.version(gradleVersion)
      }
      catch (_: IllegalArgumentException) {
        // GradleVersion.version(gradleVersion) might throw exception for custom Gradle versions
        // https://youtrack.jetbrains.com/issue/IDEA-216892
        return null
      }
    }

    private fun findProject(settings: GradleProjectSettings): Project? {
      for (project in ProjectManager.getInstance().getOpenProjects()) {
        val linkedProjectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(settings.externalProjectPath)
        if (linkedProjectSettings === settings) {
          return project
        }
      }
      return null
    }
  }
}

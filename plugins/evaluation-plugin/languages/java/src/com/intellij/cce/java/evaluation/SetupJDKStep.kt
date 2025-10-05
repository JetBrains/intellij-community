// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.java.evaluation

import com.intellij.application.options.PathMacrosImpl
import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.SetupSdkStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.resolveFromRootOrRelative
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.serialization.JpsMavenSettings.getMavenRepositoryPath
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.GradleWarmupConfigurator
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectOpenProcessor
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString

class SetupJDKStep(private val project: Project) : SetupSdkStep() {
  override val name: String = "Set up JDK step"
  override val description: String = "Configure project JDK if needed"

  override fun isApplicable(language: Language): Boolean = language in setOf(Language.JAVA, Language.KOTLIN, Language.SCALA)

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    val projectDir = project.guessProjectDir()?.let { vFile ->
      System.getenv("PROJECT_DIR_REL")?.let { vFile.resolveFromRootOrRelative(it) } ?: vFile
    } ?: throw IllegalArgumentException("Can't find project directory")

    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.run<Throwable> {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val projectSdk = projectRootManager.projectSdk
        if (projectSdk != null) {
          println("Project JDK already configured: ${projectSdk.name} (${projectSdk.homePath})")
        }
        else {
          println("Project JDK not configured")
          val sdk = configureProjectSdk(projectRootManager.projectSdkName)
          if (sdk != null) {
            println("JDK \"${sdk.name}\" (${sdk.homePath}) will be used as a project SDK")
            projectRootManager.projectSdk = sdk
          }
        }
        forceUseProjectJdkForImporter(project, projectDir)
      }
    }

    JvmBuildSystem.tryFindFor(projectDir)?.refresh(project, projectDir)

    return workspace
  }

  private fun configureProjectSdk(expectedSdkName: String?): Sdk? {
    println("Try to configure project JDK")
    val sdkTable = ProjectJdkTable.getInstance()
    val javaHome = System.getenv("EVALUATION_JAVA") ?: System.getenv("JAVA_HOME")
    if (javaHome != null) {
      println("Java found in $javaHome")
      return if (isIdeaCommunityProject()) {
        configureIdeaJdks(javaHome)
      }
      else {
        val jdkName = expectedSdkName ?: "Evaluation JDK"
        val jdk = JavaSdk.getInstance().createJdk(jdkName, javaHome, false)
        sdkTable.addJdk(jdk)
        jdk
      }
    }

    println("Java SDK not configured for the project: ${project.basePath}")
    return null
  }

  private fun configureIdeaJdks(javaHome: String): Sdk {
    println("Project (${project.basePath}) marked as IntelliJ project")
    val ideaJdk = JavaSdk.getInstance().createJdk("IDEA jdk", javaHome, false)
    val jdk = JavaSdk.getInstance().createJdk("1.8", javaHome, false)
    val sdkTable = ProjectJdkTable.getInstance()

    if (SystemInfo.isWindows) {
      println("Configuring tools.jar...")
      ideaJdk.addToolsJarToClasspath()
      jdk.addToolsJarToClasspath()
    }

    sdkTable.addJdk(ideaJdk)
    sdkTable.addJdk(jdk)

    return ideaJdk
  }

  private fun isIdeaCommunityProject(): Boolean = System.getenv("EVALUATE_ON_IDEA") != null

  private fun Sdk.addToolsJarToClasspath() {
    val javaHome = homePath
    if (javaHome == null) {
      println("Could not add tools.jar for JDK. Home path not found")
      return
    }

    val toolsJar = Paths.get(javaHome, "lib", "tools.jar")
    if (!toolsJar.exists()) {
      println("Could not add tools.jar to \"$name\" JDK")
    }
    sdkModificator.addRoot(toolsJar.toString(), OrderRootType.CLASSES)
    sdkModificator.commitChanges()
    println("tools.jar successfully added to \"$name\" JDK")
  }
}

private fun forceUseProjectJdkForImporter(project: Project, projectDir: VirtualFile) {
  when (JvmBuildSystem.tryFindFor(projectDir)) {
    JvmBuildSystem.Maven -> {
      val mavenManager = MavenProjectsManager.getInstance(project)
      mavenManager.importingSettings.jdkForImporter = ExternalSystemJdkUtil.USE_PROJECT_JDK
    }
    JvmBuildSystem.Gradle -> {
      val allGradleSettings = GradleManager.EP_NAME.extensionList.flatMap {
        val provider: AbstractExternalSystemSettings<*, *, *> = it.settingsProvider.`fun`(project)
        provider.linkedProjectsSettings
      }.filterIsInstance<GradleProjectSettings>()

      println("Found ${allGradleSettings.size} Gradle project settings linked to the project.")

      for (settings in allGradleSettings) {
        if (GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(settings)) continue
        settings.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK
      }
    }
    JvmBuildSystem.JpsIntellij -> {
      val projectHome = projectDir.path
      val m2Repo = Paths.get(getMavenRepositoryPath()).invariantSeparatorsPathString
      val jpsProject = JpsSerializationManager.getInstance().loadProject(projectHome, mapOf(PathMacrosImpl.MAVEN_REPOSITORY to m2Repo),
                                                                         true)
      val outPath = Path.of(PathUtil.getJarPathForClass(PathUtil::class.java)).parent.parent
      JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(jpsProject).outputUrl = outPath.toString()
    }
    else -> {
      println("Unknown JVM build system. No additional setup will be performed.")
    }
  }
}

enum class JvmBuildSystem {
  Gradle {
    override fun accepts(projectFile: VirtualFile): Boolean {
      return gradleProjectOpenProcessor.canOpenProject(projectFile)
    }
  },
  Maven {
    override fun accepts(projectFile: VirtualFile): Boolean {
      return mavenProjectOpenProcessor.canOpenProject(projectFile)
    }
  },
  JpsIntellij {
    override fun accepts(projectFile: VirtualFile): Boolean {
      val ultimateMarker = Paths.get(projectFile.path, "intellij.idea.ultimate.main.iml").exists()
      val communityMarker = Paths.get(projectFile.path, "intellij.idea.community.main.iml").exists()
      return ultimateMarker || communityMarker
    }
  },
  ;

  abstract fun accepts(projectFile: VirtualFile): Boolean

  companion object {
    val gradleProjectOpenProcessor by lazy {
      ProjectOpenProcessor.EXTENSION_POINT_NAME.findExtensionOrFail(GradleProjectOpenProcessor::class.java)
    }

    val mavenProjectOpenProcessor by lazy {
      ProjectOpenProcessor.EXTENSION_POINT_NAME.extensionList.first { it.name.lowercase().contains("maven") }
    }

    fun tryFindFor(projectDir: VirtualFile): JvmBuildSystem? = entries.firstOrNull { it.accepts(projectDir) }
  }

  internal fun refresh(project: Project, projectDir: VirtualFile) {
    val jvmBuildSystem = this
    runBlockingCancellable {
      when (jvmBuildSystem) {
        Gradle -> project.basePath?.let { path ->
          val file = File(path)
          println("gradle scheduleProjectRefresh")
          GradleWarmupConfigurator().prepareEnvironment(file.toPath())
          GradleWarmupConfigurator().runWarmup(project)

          println("Waiting all invoked later gradle activities...")
          repeat(10) {
            ApplicationManager.getApplication().invokeAndWait({}, ModalityState.any())
          }
        }
        Maven -> {
          Logger.getInstance("#org.jetbrains.idea.maven").setLevel(LogLevel.ALL)
          Registry.get("external.system.auto.import.disabled").setValue(false) // prevent gradle interference
          mavenProjectOpenProcessor.importProjectAfterwardsAsync(project, projectDir)
        }
        JpsIntellij -> {
          //TODO: consider to add logic? (move JpsJavaExtensionService.getInstance().getOrCreateProjectExtension?)
        }
      }
    }
  }
}
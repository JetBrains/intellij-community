package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Paths
import kotlin.io.path.exists

class SetupJDKStep(private val project: Project) : SetupSdkStep() {
  override val name: String = "Set up JDK step"
  override val description: String = "Configure project JDK if needed"

  override fun isApplicable(language: Language): Boolean = language in setOf(Language.JAVA, Language.KOTLIN, Language.SCALA)

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.run<Throwable> {
        val projectRootManager = ProjectRootManager.getInstance(project)
        val mavenManager = MavenProjectsManager.getInstance(project)
        val projectSdk = projectRootManager.projectSdk
        if (projectSdk != null) {
          println("Project JDK already configured")
        } else {
          println("Project JDK not configured")
          val sdk = configureProjectSdk(projectRootManager.projectSdkName)
          if (sdk != null) {
            println("JDK \"${sdk.name}\" (${sdk.homePath}) will be used as a project SDK")
            projectRootManager.projectSdk = sdk
          }
        }
        mavenManager.importingSettings.jdkForImporter = ExternalSystemJdkUtil.USE_PROJECT_JDK
      }
    }

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
      } else {
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
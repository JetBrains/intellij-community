// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.runInEdtAndGet
import org.assertj.core.api.BDDAssertions.then
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class AnnotationProcessorConfigImportingTest: GradleImportingTestCase() {

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor config imported in module per project mode`() {
    importProjectUsingSingeModulePerGradleProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix(
      """
      apply plugin: 'java'
      
      dependencies {
        compileOnly 'org.projectlombok:lombok:1.18.8'
        annotationProcessor 'org.projectlombok:lombok:1.18.8'
      }
    """.trimIndent()).generate())

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("An annotation processor profile should be created for Gradle module")
      .hasSize(1)

    with (moduleProcessorProfiles[0]) {
      then(isEnabled).isTrue()
      then(isObtainProcessorsFromClasspath).isFalse()
      then(processorPath).contains("lombok")
      then(moduleNames).containsExactly("project")
    }

    importProjectUsingSingeModulePerGradleProject()

    val moduleProcessorProfilesAfterReImport = config.moduleProcessorProfiles
    then(moduleProcessorProfilesAfterReImport)
      .describedAs("Duplicate annotation processor profile should not appear")
      .hasSize(1)
  }

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor modification`() {
    importProjectUsingSingeModulePerGradleProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix(
          """
      apply plugin: 'java'
      
      dependencies {
        compileOnly 'org.projectlombok:lombok:1.18.8'
        annotationProcessor 'org.projectlombok:lombok:1.18.8'
      }
    """.trimIndent()).generate())

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("An annotation processor profile should be created for Gradle module")
      .hasSize(1)

    importProjectUsingSingeModulePerGradleProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix(
          """
      apply plugin: 'java'
      
      dependencies {
        compileOnly 'com.google.dagger:dagger:2.24'
        annotationProcessor 'com.google.dagger:dagger-compiler:2.24'
      }
    """.trimIndent()).generate())

    val modifiedProfiles = config.moduleProcessorProfiles

    then(modifiedProfiles)
      .describedAs("An annotation processor should be updated, not added")
      .hasSize(1)

    with (modifiedProfiles[0]) {
      then(isEnabled).isTrue()
      then(isObtainProcessorsFromClasspath).isFalse()
      then(processorPath)
        .describedAs("annotation processor config path should point to new annotation processor")
        .contains("dagger")
      then(moduleNames).containsExactly("project")
    }
  }

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor config imported in modules per source set mode`() {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix(
          """
      apply plugin: 'java'
      
      dependencies {
        compileOnly 'org.projectlombok:lombok:1.18.8'
        annotationProcessor 'org.projectlombok:lombok:1.18.8'
      }
    """.trimIndent()).generate())

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("An annotation processor profile should be created for Gradle module")
      .hasSize(1)

    with (moduleProcessorProfiles[0]) {
      then(isEnabled).isTrue()
      then(isObtainProcessorsFromClasspath).isFalse()
      then(processorPath).contains("lombok")
      then(moduleNames).containsExactly("project.main")
    }
  }

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor config imported correctly for multimodule project`() {

    createProjectSubFile("settings.gradle", "include 'projectA', 'projectB'")

    importProject(
      createBuildScriptBuilder()
        .addPostfix(
          """
            allprojects {
              apply plugin: 'java'
              
              repositories {
                maven {
                  url 'https://repo.labs.intellij.net/repo1'
                }
              }
              
              dependencies {
                compileOnly 'org.projectlombok:lombok:1.18.8'
                annotationProcessor 'org.projectlombok:lombok:1.18.8'
              }
      }
    """.trimIndent()).generate())

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("An annotation processor profile should be created for Gradle module")
      .hasSize(1)

    with (moduleProcessorProfiles[0]) {
      then(isEnabled).isTrue()
      then(isObtainProcessorsFromClasspath).isFalse()
      then(processorPath).contains("lombok")
      then(moduleNames).contains("project.main", "project.projectA.main", "project.projectB.main")
    }
  }

  @Test
  @TargetVersions("5.2+")
  fun `test annotation processor output folders imported properly`() {

    // default location for processor output when building by IDEA
    val ideaGeneratedDir = "generated"
    createProjectSubFile("src/main/$ideaGeneratedDir/Generated.java",
                         "public class Generated {}")
    // default location for processor output when building by Gradle
    val gradleGeneratedDir = "build/generated/sources/annotationProcessor/java/main"
    createProjectSubFile("$gradleGeneratedDir/Generated.java",
                         "public class Generated {}")

    val config = createBuildScriptBuilder()
      .withJavaPlugin()
      .withMavenCentral()
      .addPostfix(
        """
      dependencies {
        annotationProcessor 'org.projectlombok:lombok:1.18.8'
      }
    """.trimIndent()).generate()

    // import with default settings: delegate build to gradle
    importProject(config)
    assertSources("project.main", gradleGeneratedDir)
    assertGeneratedSources("project.main", gradleGeneratedDir)

    currentExternalProjectSettings.delegatedBuild = false

    // import with build by intellij idea
    importProject(config)
    assertSources("project.main", ideaGeneratedDir)
    assertGeneratedSources("project.main", ideaGeneratedDir)

    // subscribe to build delegation changes in current project
    (ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID) as GradleManager).runActivity(myProject)
    // switch delegation to gradle
    currentExternalProjectSettings.delegatedBuild = true
    GradleSettings.getInstance(myProject).publisher.onBuildDelegationChange(true, projectPath)
    assertSources("project.main", gradleGeneratedDir)
    assertGeneratedSources("project.main", gradleGeneratedDir)

    // switch delegation to idea
    currentExternalProjectSettings.delegatedBuild = false
    GradleSettings.getInstance(myProject).publisher.onBuildDelegationChange(false, projectPath)
    assertSources("project.main", ideaGeneratedDir)
    assertGeneratedSources("project.main", ideaGeneratedDir)
  }

  @Test
  @TargetVersions("4.6+")
  fun `test two different annotation processors`() {
    createProjectSubFile("settings.gradle", "include 'project1','project2'")
    importProject(
      createBuildScriptBuilder()
        .withMavenCentral()
        .addPostfix(
          """
            |  allprojects { apply plugin: 'java' }
            |  project("project1") {
            |      dependencies {
            |        compileOnly 'org.projectlombok:lombok:1.18.8'
            |        annotationProcessor 'org.projectlombok:lombok:1.18.8'
            |      }
            |  }
            |  
            |  project("project2") {
            |    dependencies {
            |        compileOnly 'com.google.dagger:dagger:2.24'
            |        annotationProcessor 'com.google.dagger:dagger-compiler:2.24'
            |    }
            |  }
    """.trimMargin()).generate())

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("Annotation processors profiles should be created correctly")
      .hasSize(2)
      .anyMatch {
        it.isEnabled && !it.isObtainProcessorsFromClasspath
        && it.processorPath.contains("lombok")
        && it.moduleNames == setOf("project.project1.main")
      }
      .anyMatch {
        it.isEnabled && !it.isObtainProcessorsFromClasspath
        && it.processorPath.contains("dagger")
        && it.moduleNames == setOf("project.project2.main")
      }
  }

  @Test
  @TargetVersions("4.6+")
   fun `test change modules included in processor profile`() {
       createProjectSubFile("settings.gradle", "include 'project1','project2'")
       importProject(
         createBuildScriptBuilder()
           .withMavenCentral()
           .addPostfix(
             """
            |  allprojects { apply plugin: 'java' }
            |  project("project1") {
            |      dependencies {
            |        compileOnly 'org.projectlombok:lombok:1.18.8'
            |        annotationProcessor 'org.projectlombok:lombok:1.18.8'
            |      }
            |  }
    """.trimMargin()).generate())

    then((CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl)
           .moduleProcessorProfiles)
      .describedAs("Annotation processor profile includes wrong module")
      .extracting("moduleNames")
      .containsExactly(setOf("project.project1.main"))

    importProject(
      createBuildScriptBuilder()
        .withMavenCentral()
        .addPostfix(
          """
            |  allprojects { apply plugin: 'java' }
            |  project("project2") {
            |      dependencies {
            |        compileOnly 'org.projectlombok:lombok:1.18.8'
            |        annotationProcessor 'org.projectlombok:lombok:1.18.8'
            |      }
            |  }
    """.trimMargin()).generate())

    then((CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl)
           .moduleProcessorProfiles)
         .describedAs("Annotation processor profile includes wrong module")
         .extracting("moduleNames")
         .containsExactly(setOf("project.project2.main"))
   }

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor with transitive deps`() {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix(
          """
      apply plugin: 'java'
      
      dependencies {
        annotationProcessor 'junit:junit:4.12' // this is not an annotation processor, but has transitive deps
      }
    """.trimIndent()).generate())

    then((CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl)
           .moduleProcessorProfiles[0]
           .processorPath)
      .describedAs("Annotation processor path should include junit and hamcrest")
      .contains("junit", "hamcrest")
  }

  @Test
  @TargetVersions("4.3+")
  fun `test gradle-apt-plugin settings are imported`() {
    importProject(
      createBuildScriptBuilder()
        .withMavenCentral()
        .addBuildScriptRepository("maven { url 'https://repo.labs.intellij.net/plugins-gradle-org' }")
        .addBuildScriptClasspath("net.ltgt.gradle:gradle-apt-plugin:0.21")
        .addPostfix("""
          apply plugin: "net.ltgt.apt"
          apply plugin: 'java'
          
          dependencies {
            compileOnly("org.immutables:value-annotations:2.7.1")
            annotationProcessor("org.immutables:value:2.7.1")
          }
        """.trimIndent()).generate())

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("An annotation processor profile should be created for Gradle module")
      .hasSize(1)

    with (moduleProcessorProfiles[0]) {
      then(isEnabled).isTrue()
      then(isObtainProcessorsFromClasspath).isFalse()
      then(processorPath).contains("immutables")
      then(moduleNames).containsExactly("project.main")
    }
  }


  @Test
  @TargetVersions("3.4+")
  fun `test custom annotation processor configurations are imported`() {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix("""
      apply plugin: 'java'
      
      configurations {
       apt
      }
      
      dependencies {
        compileOnly("org.immutables:value-annotations:2.7.1")
        apt("org.immutables:value:2.7.1")
      }
      
      compileJava {
        options.annotationProcessorPath = configurations.apt
      }
    """.trimIndent()).generate())

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("An annotation processor profile should be created for Gradle module")
      .hasSize(1)

    with (moduleProcessorProfiles[0]) {
      then(isEnabled).isTrue()
      then(isObtainProcessorsFromClasspath).isFalse()
      then(processorPath).contains("immutables")
      then(moduleNames).containsExactly("project.main")
    }
  }

  @Test
  @TargetVersions("5.2+")
  fun `test annotation processor profiles of non gradle projects are not removed`() {
    val nonGradleModule = runInEdtAndGet {
      ApplicationManager.getApplication().runWriteAction(Computable {
        ModuleManager.getInstance(myProject).newModule(myProject.basePath!! + "/java_module", ModuleTypeId.JAVA_MODULE)
      })
    }

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val processorConfigProfileImpl = ProcessorConfigProfileImpl("other")
    processorConfigProfileImpl.addModuleName(nonGradleModule.name)
    config.addModuleProcessorProfile(processorConfigProfileImpl)

    importProject(
      createBuildScriptBuilder()
        .withJavaLibraryPlugin()
        .addCompileOnlyDependency("org.projectlombok:lombok:1.18.8")
        .addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
        .generate())

    val annotationProcessingConfiguration = config.getAnnotationProcessingConfiguration(nonGradleModule)

    then(annotationProcessingConfiguration.name)
      .isEqualTo("other")
  }
}
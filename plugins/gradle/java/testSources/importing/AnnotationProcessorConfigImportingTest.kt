// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID_NAME
import org.assertj.core.api.BDDAssertions.then
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

class AnnotationProcessorConfigImportingTest: GradleImportingTestCase() {

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor config imported in module per project mode`() {
    currentExternalProjectSettings.isResolveModulePerSourceSet = false

    importProject {
      withJavaPlugin()
      withMavenCentral()
      addDependency("compileOnly", "org.projectlombok:lombok:1.18.8")
      addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
    }

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

    importProject()

    val moduleProcessorProfilesAfterReImport = config.moduleProcessorProfiles
    then(moduleProcessorProfilesAfterReImport)
      .describedAs("Duplicate annotation processor profile should not appear")
      .hasSize(1)
  }

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor modification in module per project mode`() {
    currentExternalProjectSettings.isResolveModulePerSourceSet = false

    importProject {
      withJavaPlugin()
      withMavenCentral()
      addDependency("compileOnly", "org.projectlombok:lombok:1.18.8")
      addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
    }

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    val moduleProcessorProfiles = config.moduleProcessorProfiles

    then(moduleProcessorProfiles)
      .describedAs("An annotation processor profile should be created for Gradle module")
      .hasSize(1)

    importProject {
      withJavaPlugin()
      withMavenCentral()
      addDependency("compileOnly", "com.google.dagger:dagger:2.24")
      addDependency("annotationProcessor", "com.google.dagger:dagger-compiler:2.24")
    }

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
    importProject {
      withJavaPlugin()
      withMavenCentral()
      addDependency("compileOnly", "org.projectlombok:lombok:1.18.8")
      addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
    }

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

    createProjectSubFile("settings.gradle", including("projectA", "projectB"))

    importProject {
      allprojects {
        withMavenCentral()
        withJavaPlugin()
        addDependency("compileOnly", "org.projectlombok:lombok:1.18.8")
        addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
      }
    }

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
    val ideaGeneratedDir = "src/main/generated"
    createProjectSubFile("$ideaGeneratedDir/Generated.java",
                         "public class Generated {}")
    // default location for processor output when building by Gradle
    val gradleGeneratedDir = "build/generated/sources/annotationProcessor/java/main"
    createProjectSubFile("$gradleGeneratedDir/Generated.java",
                         "public class Generated {}")

    createBuildFile {
      withJavaPlugin()
      withMavenCentral()
      addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
    }

    // import with default settings: delegate build to gradle
    importProject()

    assertSourceRoots("project.main") {
      it.sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path(gradleGeneratedDir))
    }

    currentExternalProjectSettings.delegatedBuild = false

    // import with build by intellij idea
    importProject()
    assertSourceRoots("project.main") {
      it.sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path(ideaGeneratedDir))
    }

    // subscribe to build delegation changes in current project
    (ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID) as GradleManager).runActivity(myProject)
    // switch delegation to gradle
    currentExternalProjectSettings.delegatedBuild = true
    GradleSettings.getInstance(myProject).publisher.onBuildDelegationChange(true, projectPath)
    assertSourceRoots("project.main") {
      it.sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path(gradleGeneratedDir))
    }

    // switch delegation to idea
    currentExternalProjectSettings.delegatedBuild = false
    GradleSettings.getInstance(myProject).publisher.onBuildDelegationChange(false, projectPath)
    assertSourceRoots("project.main") {
      it.sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path(ideaGeneratedDir))
    }
  }

  @Test
  @TargetVersions("4.6+")
  fun `test two different annotation processors`() {
    createProjectSubFile("settings.gradle", including("project1", "project2"))

    importProject {
      allprojects {
        withMavenCentral()
        withJavaPlugin()
      }
      project("project1") {
        addDependency("compileOnly", "org.projectlombok:lombok:1.18.8")
        addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
      }
      project("project2") {
        addDependency("compileOnly", "com.google.dagger:dagger:2.24")
        addDependency("annotationProcessor", "com.google.dagger:dagger-compiler:2.24")
      }
    }

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
    createProjectSubFile("settings.gradle", including("project1", "project2"))
    importProject {
      allprojects {
        withMavenCentral()
        withJavaPlugin()
      }
      project("project1") {
        addDependency("compileOnly", "org.projectlombok:lombok:1.18.8")
        addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
      }
    }

    then((CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl)
           .moduleProcessorProfiles)
      .describedAs("Annotation processor profile includes wrong module")
      .extracting("moduleNames")
      .containsExactly(setOf("project.project1.main"))

    importProject {
      allprojects {
        withMavenCentral()
        withJavaPlugin()
      }
      project("project2") {
        addDependency("compileOnly", "org.projectlombok:lombok:1.18.8")
        addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
      }
    }

    then((CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl)
           .moduleProcessorProfiles)
         .describedAs("Annotation processor profile includes wrong module")
         .extracting("moduleNames")
         .containsExactly(setOf("project.project2.main"))
   }

  @Test
  @TargetVersions("4.6+")
  fun `test annotation processor with transitive deps`() {
    importProject {
      withJavaPlugin()
      withMavenCentral()
      // this is not an annotation processor but has transitive deps
      addDependency("annotationProcessor", "junit:junit:4.12")
    }

    then((CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl)
           .moduleProcessorProfiles[0]
           .processorPath)
      .describedAs("Annotation processor path should include junit and hamcrest")
      .contains("junit", "hamcrest")
  }

  @Test
  @TargetVersions("<=8.1")
  fun `test gradle-apt-plugin settings are imported`() {
    importProject {
      withMavenCentral()
      withBuildScriptRepository {
        mavenRepository("https://repo.labs.intellij.net/plugins-gradle-org")
      }
      addBuildScriptClasspath("net.ltgt.gradle:gradle-apt-plugin:0.21")
      applyPlugin("net.ltgt.apt")
      applyPlugin("java")
      addDependency("compileOnly", "org.immutables:value-annotations:2.7.1")
      addDependency("annotationProcessor", "org.immutables:value:2.7.1")
    }

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
  fun `test custom annotation processor configurations are imported`() {
    importProject {
      withJavaPlugin()
      withMavenCentral()
      addDependency("compileOnly", "org.immutables:value-annotations:2.7.1")
      addDependency("apt", "org.immutables:value:2.7.1")
      addPrefix("""      
        |configurations {
        |  apt
        |}
        |compileJava {
        |  options.annotationProcessorPath = configurations.apt
        |}
      """.trimMargin())
    }

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
        ModuleManager.getInstance(myProject).newModule(myProject.basePath!! + "/java_module", JAVA_MODULE_ENTITY_TYPE_ID_NAME)
      })
    }

    val config = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    config.addNewProcessorProfile("other").addModuleName(nonGradleModule.name)

    importProject {
      withJavaLibraryPlugin()
      addCompileOnlyDependency("org.projectlombok:lombok:1.18.8")
      addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
    }

    val annotationProcessingConfiguration = config.getAnnotationProcessingConfiguration(nonGradleModule)

    then(annotationProcessingConfiguration.name)
      .isEqualTo("other")
  }

  @Test
  @TargetVersions("5.6+")
  fun `test annotation processor generated sources for java-test-fixtures`() {
    val annotationProcessor = "build/generated/sources/annotationProcessor"

    createProjectSubDir("src/main/java")
    createProjectSubDir("src/main/resources")
    createProjectSubDir("src/test/java")
    createProjectSubDir("src/test/resources")
    createProjectSubDir("src/testFixtures/java")
    createProjectSubDir("src/testFixtures/resources")
    createProjectSubDir("$annotationProcessor/java/main")
    createProjectSubDir("$annotationProcessor/java/test")
    createProjectSubDir("$annotationProcessor/java/testFixtures")

    importProject {
      withJavaPlugin()
      withMavenCentral()
      withPlugin("java-test-fixtures")
      addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
      addDependency("testAnnotationProcessor", "org.projectlombok:lombok:1.18.8")
      addDependency("testFixturesAnnotationProcessor", "org.projectlombok:lombok:1.18.8")
    }

    assertModules("project", "project.main", "project.test", "project.testFixtures")

    assertContentRoots("project", projectPath)
    assertNoSourceRoots("project")

    assertContentRoots("project.main", path("src/main"), path("$annotationProcessor/java/main"))
    assertSourceRoots("project.main") {
      it.sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      it.sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path("$annotationProcessor/java/main"))
      it.sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    }

    assertContentRoots("project.test", path("src/test"), path("$annotationProcessor/java/test"))
    assertSourceRoots("project.test") {
      it.sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      it.sourceRoots(ExternalSystemSourceType.TEST_GENERATED, path("$annotationProcessor/java/test"))
      it.sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    }

    assertContentRoots("project.testFixtures", path("src/testFixtures"), path("$annotationProcessor/java/testFixtures"))
    assertSourceRoots("project.testFixtures") {
      it.sourceRoots(ExternalSystemSourceType.TEST, path("src/testFixtures/java"))
      it.sourceRoots(ExternalSystemSourceType.TEST_GENERATED, path("$annotationProcessor/java/testFixtures"))
      it.sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/testFixtures/resources"))
    }
  }

  @Test
  @TargetVersions("7.4+")
  fun `test annotation processor generated sources for jvm-test-suite`() {
    val annotationProcessor = "build/generated/sources/annotationProcessor"

    createProjectSubDir("src/main/java")
    createProjectSubDir("src/main/resources")
    createProjectSubDir("src/test/java")
    createProjectSubDir("src/test/resources")
    createProjectSubDir("src/integrationTest/java")
    createProjectSubDir("src/integrationTest/resources")
    createProjectSubDir("$annotationProcessor/java/main")
    createProjectSubDir("$annotationProcessor/java/test")
    createProjectSubDir("$annotationProcessor/java/integrationTest")

    importProject {
      withJavaPlugin()
      withMavenCentral()
      withPlugin("jvm-test-suite")
      addDependency("annotationProcessor", "org.projectlombok:lombok:1.18.8")
      addDependency("testAnnotationProcessor", "org.projectlombok:lombok:1.18.8")
      withPostfix {
        call("testing") {
          call("suites") {
            call("integrationTest", code("JvmTestSuite")) {
              call("dependencies") {
                call("annotationProcessor", "org.projectlombok:lombok:1.18.8")
              }
            }
          }
        }
      }
    }

    assertModules("project", "project.main", "project.test", "project.integrationTest")

    assertContentRoots("project", projectPath)
    assertNoSourceRoots("project")

    assertContentRoots("project.main", path("src/main"), path("$annotationProcessor/java/main"))
    assertSourceRoots("project.main") {
      it.sourceRoots(ExternalSystemSourceType.SOURCE, path("src/main/java"))
      it.sourceRoots(ExternalSystemSourceType.SOURCE_GENERATED, path("$annotationProcessor/java/main"))
      it.sourceRoots(ExternalSystemSourceType.RESOURCE, path("src/main/resources"))
    }

    assertContentRoots("project.test", path("src/test"), path("$annotationProcessor/java/test"))
    assertSourceRoots("project.test") {
      it.sourceRoots(ExternalSystemSourceType.TEST, path("src/test/java"))
      it.sourceRoots(ExternalSystemSourceType.TEST_GENERATED, path("$annotationProcessor/java/test"))
      it.sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/test/resources"))
    }

    assertContentRoots("project.integrationTest", path("src/integrationTest"), path("$annotationProcessor/java/integrationTest"))
    assertSourceRoots("project.integrationTest") {
      it.sourceRoots(ExternalSystemSourceType.TEST, path("src/integrationTest/java"))
      it.sourceRoots(ExternalSystemSourceType.TEST_GENERATED, path("$annotationProcessor/java/integrationTest"))
      it.sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, path("src/integrationTest/resources"))
    }
  }
}
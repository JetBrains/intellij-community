// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.quarantine.dsl

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrDeprecatedAPIUsageInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest

class GradleHighlightingTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testConfiguration(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      testHighlighting("""
        |configurations.create("myConfiguration")
        |configurations.myConfiguration {
        |    transitive = false
        |}
      """.trimMargin())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testConfigurationResolve(gradleVersion: GradleVersion) {
    test(gradleVersion, MY_CONFIGURATION_FIXTURE) {
      val file = getFile("build.gradle")
      runReadAction {
        val psiFile = fixture.psiManager.findFile(file)!!
        val offset = file.readText().indexOf("transitive") + 1
        val reference = psiFile.findReferenceAt(offset)!!
        val method = assertInstanceOf<PsiMethod>(reference.resolve())
        assertEquals("setTransitive", method.name)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGeneratedSetter(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      fixture.enableInspections(GroovyAssignabilityCheckInspection::class.java)
      testHighlighting("""
        jar {
          archiveClassifier = "a"
        }
      """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testActionDelegate(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      fixture.enableInspections(GrUnresolvedAccessInspection::class.java)
      testHighlighting("""
        tasks.register('jc', Jar) {
            archiveBaseName
        }
      """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNamedApplication(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      fixture.enableInspections(GrUnresolvedAccessInspection::class.java)
      fixture.enableInspections(GroovyAssignabilityCheckInspection::class.java)
      testHighlighting("""
        tasks.named("compileJava", JavaCompile) {
        }
      """.trimIndent())
    }
  }


  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGeneratedSetter2(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      fixture.enableInspections(GroovyAssignabilityCheckInspection::class.java)
      testHighlighting("""
        tasks.register('jc', Jar) {
            archiveBaseName = 'abcde'
        }
      """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGeneratedSetter3(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      fixture.enableInspections(GroovyAssignabilityCheckInspection::class.java)
      testHighlighting("""
        tasks.withType(JavaCompile).configureEach {
            options.verbose = true
        }
      """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGradleGroovyImplicitUsages(gradleVersion: GradleVersion) {
    test(gradleVersion, BUILD_SRC_FIXTURE) {
      fixture.enableInspections(GroovyUnusedDeclarationInspection(), UnusedDeclarationInspectionBase(true))
      testHighlighting("buildSrc/src/main/groovy/org/example/GrTask.groovy", """
        |package org.example
        |
        |import org.gradle.api.DefaultTask
        |import org.gradle.api.tasks.Classpath
        |import org.gradle.api.tasks.Console
        |import org.gradle.api.tasks.Destroys
        |import org.gradle.api.tasks.Input
        |import org.gradle.api.tasks.InputDirectory
        |import org.gradle.api.tasks.InputFile
        |import org.gradle.api.tasks.InputFiles
        |import org.gradle.api.tasks.LocalState
        |import org.gradle.api.tasks.OutputDirectories
        |import org.gradle.api.tasks.OutputDirectory
        |import org.gradle.api.tasks.OutputFile
        |import org.gradle.api.tasks.TaskAction
        |import org.gradle.api.file.FileCollection
        |
        |class <warning>GrTask</warning> extends DefaultTask {
        |    @Input String inputString
        |    @InputFile File inputFile
        |    @InputFiles FileCollection inputFiles
        |    @InputDirectory File inputDirectory
        |    @OutputDirectory File outputDirectory
        |    @OutputDirectories FileCollection outputDirectories
        |    @OutputFile File outputFile
        |    @LocalState File localStateFile
        |    @Destroys File destroyedFile
        |    @Classpath FileCollection classpath
        |    @Console String consoleString
        |
        |    @TaskAction
        |    private void action() {
        |    }
        |}
      """.trimMargin())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test runtime decoration of Action to Closure`(gradleVersion: GradleVersion) {
    test(gradleVersion, BUILD_SRC_FIXTURE_2) {
      fixture.enableInspections(GroovyAssignabilityCheckInspection::class.java)
      testHighlighting("""
        task grr(type: GrTask) {
            foo {}
        }
      """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGradleJavaImplicitUsages(gradleVersion: GradleVersion) {
    test(gradleVersion, BUILD_SRC_FIXTURE) {
      fixture.enableInspections(GroovyUnusedDeclarationInspection(), UnusedDeclarationInspectionBase(true))
      testHighlighting("buildSrc/src/main/java/org/example/JavaTask.java", """
        |package org.example;
        |
        |import org.gradle.api.DefaultTask;
        |import org.gradle.api.tasks.*;
        |import org.gradle.api.file.FileCollection;
        |
        |import java.io.File;
        |
        |public class <warning descr="Class 'JavaTask' is never used">JavaTask</warning> extends DefaultTask {
        |    private String inputString;
        |    private String unusedField;
        |
        |    @TaskAction
        |    private void action() {
        |    }
        |
        |    public String <warning>getUnusedField</warning>() {
        |        return unusedField;
        |    }
        |
        |    public void <warning>setUnusedField</warning>(String unusedField) {
        |        this.unusedField = unusedField;
        |    }
        |
        |    @Input
        |    public String getInputString() {
        |        return inputString;
        |    }
        |
        |    public void setInputString(String inputString) {
        |        this.inputString = inputString;
        |    }
        |
        |    @InputFile
        |    public File getInputFile() {
        |        return null;
        |    }
        |
        |    @InputFiles
        |    public FileCollection getInputFiles() {
        |        return null;
        |    }
        |
        |    @InputDirectory
        |    public File getInputDirectory() {
        |        return null;
        |    }
        |
        |    @OutputDirectory
        |    public File getOutputDirectory() {
        |        return null;
        |    }
        |
        |    @OutputDirectories
        |    public FileCollection getOutputDirectories() {
        |        return null;
        |    }
        |
        |    @OutputFile
        |    public File getOutputFile() {
        |        return null;
        |    }
        |
        |    @LocalState
        |    public File getLocalStateFile() {
        |        return null;
        |    }
        |
        |    @Destroys
        |    public File getDestroyedFile() {
        |        return null;
        |    }
        |
        |    @Classpath
        |    public FileCollection getClasspath() {
        |        return null;
        |    }
        |
        |    @Console
        |    public String getConsoleString() {
        |        return null;
        |    }
        |}
      """.trimMargin())
    }
  }

  /** @see org.jetbrains.plugins.gradle.service.resolve.transformation.GradleActionToClosureMemberContributor */
  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test deprecation of generated method with Closure instead of Action` (gradleVersion: GradleVersion) {
    assumeThatGradleIsAtLeast(gradleVersion, "8.12") { "`tasks.create` is deprecated since 8.12" }
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GrDeprecatedAPIUsageInspection::class.java)
      testHighlighting("tasks.<warning descr=\"'create' is deprecated\">create</warning>(\"foo\", Copy) {}")
    }
  }

  companion object {

    private val MY_CONFIGURATION_FIXTURE = GradleTestFixtureBuilder.create("GradleHighlightingTest-myConfiguration") { gradleVersion ->
      withSettingsFile {
        setProjectName("GradleHighlightingTest-myConfiguration")
      }
      withBuildFile(gradleVersion) {
        withPrefix {
          call("configurations.create", "myConfiguration")
          call("configurations.myConfiguration") {
            assign("transitive", false)
          }
        }
      }
    }

    private val BUILD_SRC_FIXTURE = GradleTestFixtureBuilder.create("GradleHighlightingTest-buildSrc") {
      withSettingsFile {
        setProjectName("GradleHighlightingTest-buildSrc")
      }
      withDirectory("buildSrc/src/main/groovy")
      withDirectory("buildSrc/src/main/java")
      withFile("buildSrc/settings.gradle", "")
    }

    private val BUILD_SRC_FIXTURE_2 = GradleTestFixtureBuilder.create("GradleHighlightingTest-buildSrc2") {
      withSettingsFile {
        setProjectName("GradleHighlightingTest-buildSrc2")
      }
      withFile("buildSrc/src/main/groovy/GrTask.groovy", """
        import org.gradle.api.Action
        import org.gradle.api.DefaultTask;
        
        class GrTask extends DefaultTask {
            @FunctionalInterface
            static interface FunInterface1 { void foo(String s) }
        
            void foo(FunInterface1 _) {}
            void foo(Action<? super String> _) {}
        }
      """.trimIndent())
    }
  }
}
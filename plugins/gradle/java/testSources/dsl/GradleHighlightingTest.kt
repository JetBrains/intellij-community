// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.openapi.externalSystem.util.runReadAction
import com.intellij.openapi.externalSystem.util.textContent
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder.Companion.EMPTY_PROJECT
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder.Companion.JAVA_PROJECT
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest

class GradleHighlightingTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testConfiguration(gradleVersion: GradleVersion) {
    test(gradleVersion, EMPTY_PROJECT) {
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
        val offset = file.textContent.indexOf("transitive") + 1
        val reference = psiFile.findReferenceAt(offset)!!
        val method = assertInstanceOf<PsiMethod>(reference.resolve())
        assertEquals("setTransitive", method.name)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGeneratedSetter(gradleVersion: GradleVersion) {
    test(gradleVersion, JAVA_PROJECT) {
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
        |public class JavaTask extends DefaultTask {
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

  companion object {
    private val MY_CONFIGURATION_FIXTURE = GradleTestFixtureBuilder.buildFile("GradleHighlightingTest-myConfiguration") {
      withPrefix {
        call("configurations.create", "myConfiguration")
        call("configurations.myConfiguration") {
          assign("transitive", false)
        }
      }
    }

    private val BUILD_SRC_FIXTURE = GradleTestFixtureBuilder.create("GradleHighlightingTest-buildSrc") {
      withSettingsFile {
        setProjectName("GradleHighlightingTest-buildSrc")
      }
      withDirectory("buildSrc/src/main/groovy")
      withDirectory("buildSrc/src/main/java")
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
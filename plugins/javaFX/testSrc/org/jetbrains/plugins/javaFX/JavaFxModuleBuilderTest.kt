// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX

import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupTestModule
import com.intellij.ide.starters.shared.*
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4
import org.jetbrains.plugins.javaFX.wizard.JavaFxModuleBuilder
import org.junit.Test

class JavaFxModuleBuilderTest : LightJavaCodeInsightFixtureTestCase4(JAVA_11) {
  @Test
  fun emptyMavenProject() {
    JavaFxModuleBuilder().setupTestModule(fixture.module) {
      language = JAVA_STARTER_LANGUAGE
      projectType = MAVEN_PROJECT
      testFramework = JUNIT_TEST_RUNNER
      isCreatingNewProject = true
    }

    expectFile("src/main/java/com/example/demo/HelloApplication.java", """
      package com.example.demo;

      import javafx.application.Application;
      import javafx.fxml.FXMLLoader;
      import javafx.scene.Scene;
      import javafx.stage.Stage;

      import java.io.IOException;

      public class HelloApplication extends Application {
          @Override
          public void start(Stage stage) throws IOException {
              FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
              Scene scene = new Scene(fxmlLoader.load(), 320, 240);
              stage.setTitle("Hello!");
              stage.setScene(scene);
              stage.show();
          }

          public static void main(String[] args) {
              launch();
          }
      }
    """.trimIndent())

    val dlr = "\$"
    expectFile("pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>

          <groupId>com.example</groupId>
          <artifactId>demo</artifactId>
          <version>1.0-SNAPSHOT</version>
          <name>light_idea_test_case</name>

          <properties>
              <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              <junit.version>5.8.2</junit.version>
          </properties>

          <dependencies>
              <dependency>
                  <groupId>org.openjfx</groupId>
                  <artifactId>javafx-controls</artifactId>
                  <version>11.0.2</version>
              </dependency>
              <dependency>
                  <groupId>org.openjfx</groupId>
                  <artifactId>javafx-fxml</artifactId>
                  <version>11.0.2</version>
              </dependency>

              <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-api</artifactId>
                  <version>${dlr}{junit.version}</version>
                  <scope>test</scope>
              </dependency>
              <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter-engine</artifactId>
                  <version>${dlr}{junit.version}</version>
                  <scope>test</scope>
              </dependency>
          </dependencies>

          <build>
              <plugins>
                  <plugin>
                      <groupId>org.apache.maven.plugins</groupId>
                      <artifactId>maven-compiler-plugin</artifactId>
                      <version>3.9.0</version>
                      <configuration>
                          <source>11</source>
                          <target>11</target>
                      </configuration>
                  </plugin>
                  <plugin>
                      <groupId>org.openjfx</groupId>
                      <artifactId>javafx-maven-plugin</artifactId>
                      <version>0.0.8</version>
                      <executions>
                          <execution>
                              <!-- Default configuration for running with: mvn clean javafx:run -->
                              <id>default-cli</id>
                              <configuration>
                                  <mainClass>com.example.demo/com.example.demo.HelloApplication</mainClass>
                                  <launcher>app</launcher>
                                  <jlinkZipName>app</jlinkZipName>
                                  <jlinkImageName>app</jlinkImageName>
                                  <noManPages>true</noManPages>
                                  <stripDebug>true</stripDebug>
                                  <noHeaderFiles>true</noHeaderFiles>
                              </configuration>
                          </execution>
                      </executions>
                  </plugin>
              </plugins>
          </build>
      </project>
    """.trimIndent())
  }

  @Test
  fun emptyGradleProject() {
    JavaFxModuleBuilder().setupTestModule(fixture.module) {
      language = JAVA_STARTER_LANGUAGE
      projectType = GRADLE_PROJECT
      testFramework = TESTNG_TEST_RUNNER
      isCreatingNewProject = true
    }

    expectFile("src/main/java/com/example/demo/HelloController.java", """
      package com.example.demo;

      import javafx.fxml.FXML;
      import javafx.scene.control.Label;
      
      public class HelloController {
          @FXML
          private Label welcomeText;
      
          @FXML
          protected void onHelloButtonClick() {
              welcomeText.setText("Welcome to JavaFX Application!");
          }
      }
    """.trimIndent())
    val dlr = "\$"
    expectFile("build.gradle", """
      plugins {
          id 'java'
          id 'application'
          id 'org.openjfx.javafxplugin' version '0.0.10'
          id 'org.beryx.jlink' version '2.24.1'
      }

      group 'com.example'
      version '1.0-SNAPSHOT'

      repositories {
          mavenCentral()
      }

      sourceCompatibility = '11'
      targetCompatibility = '11'

      tasks.withType(JavaCompile) {
          options.encoding = 'UTF-8'
      }

      application {
          mainModule = 'com.example.demo'
          mainClass = 'com.example.demo.HelloApplication'
      }

      javafx {
          version = '11.0.2'
          modules = ['javafx.controls', 'javafx.fxml']
      }

      dependencies {

          testImplementation('org.testng:testng:7.5')
      }

      test {
          useTestNG()
      }
      
      jlink {
          imageZip = project.file("${dlr}{buildDir}/distributions/app-${dlr}{javafx.platform.classifier}.zip")
          options = ['--strip-debug', '--compress', '2', '--no-header-files', '--no-man-pages']
          launcher {
              name = 'app'
          }
      }

      jlinkZip {
          group = 'distribution'
      }
    """.trimIndent())

    expectFile("settings.gradle", """
      rootProject.name = "demo"
    """.trimIndent())
  }

  private fun expectFile(path: String, content: String) {
    fixture.configureFromTempProjectFile(path)
    fixture.checkResult(content)
  }
}
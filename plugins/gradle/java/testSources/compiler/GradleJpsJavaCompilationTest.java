// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.compiler;

import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import org.junit.Test;

import java.io.IOException;

public class GradleJpsJavaCompilationTest extends GradleJpsCompilingTestCase {
  @Test
  public void testCustomSourceSetDependencies() throws IOException {
    ExternalProjectsManagerImpl.getInstance(getMyProject()).setStoreExternally(true);
    createProjectSubFile("src/intTest/java/DepTest.java", "class DepTest extends CommonTest {}");
    createProjectSubFile("src/test/java/CommonTest.java", "public class CommonTest {}");
    importProject("""
                    apply plugin: 'java'
                    sourceSets {
                      intTest {
                         compileClasspath += main.output + test.output  }
                    }""");
    compileModules("project.main", "project.test", "project.intTest");
  }

  @Test
  public void testDifferentTargetCompatibilityForProjectAndModules() throws IOException {
    ExternalProjectsManagerImpl.getInstance(getMyProject()).setStoreExternally(true);
    createProjectSubFile(
      "src/main/java/Main.java",
      """
        public class Main {
            public static void main(String[] args) {
                run(() -> System.out.println("Hello Home!"));
            }

            public static void run(Runnable runnable) {
                runnable.run();
            }
        }
        """);
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .sourceCompatibility("7")
        .targetCompatibility("7")
        .withPrefix(it -> {
          it.call("compileJava", it1 -> {
            it1.assign("sourceCompatibility", "8");
            it1.assign("targetCompatibility", "8");
          });
        })
        .generate()
    );
    compileModules("project.main");
  }

  @Override
  protected boolean useDirectoryBasedStorageFormat() {
    return true;
  }
}

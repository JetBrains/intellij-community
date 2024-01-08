// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.gradle.service.project.GradleAutoImportAware;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

import java.io.File;

public class GradleAutoImportAwareTest extends GradleImportingTestCase {

  @Test
  public void testCompilerOutputNotWatched() throws Exception {
    createProjectSubFile("src/main/java/my/pack/gradle/A.java");
    final VirtualFile file = createProjectSubFile("buildScripts/myScript.gradle");
    importProjectUsingSingeModulePerGradleProject("apply plugin: 'java'");

    assertModules("project");

    final GradleAutoImportAware gradleAutoImportAware = new GradleAutoImportAware();

    final Module module = ModuleManager.getInstance(myProject).getModules()[0];
    final CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
    final String compilerOutputDir = VfsUtilCore.urlToPath(compilerModuleExtension.getCompilerOutputUrl() + "/my/pack/gradle");
    final String testDataOutputDir = VfsUtilCore.urlToPath(compilerModuleExtension.getCompilerOutputUrlForTests() + "/testData.gradle");

    assertNull(gradleAutoImportAware.getAffectedExternalProjectPath(compilerOutputDir, myProject));
    assertNull(gradleAutoImportAware.getAffectedExternalProjectPath(testDataOutputDir, myProject));

    assertNotNull(gradleAutoImportAware.getAffectedExternalProjectPath(file.getPath(), myProject));
  }

  @Test
  @TargetVersions("7.0.0+")
  public void testLibsVersionTomlIsWatched() throws Exception {
    createProjectSubFile("gradle/libs.versions.toml", """
      [libraries]
      jb-annotations = { module = "org.jetbrains:annotations", version = "16.0.2" }
      """);

    if (isGradleOlderOrSameAs("7.0.2")) {
      createSettingsFile("enableFeaturePreview('VERSION_CATALOGS')");
    }

    importProject();

    final GradleAutoImportAware gradleAutoImportAware = new GradleAutoImportAware();
    assertContain(gradleAutoImportAware.getAffectedExternalProjectFiles(getProjectPath(), myProject),
                  new File(getProjectPath() + "/gradle/libs.versions.toml"));
  }

  @Test
  @TargetVersions("7.0.2+")
  public void testCustomVersionCatalogTomlIsWatched() throws Exception {
    var settingsFile = new StringBuilder();

    if (isGradleOlderOrSameAs("7.0.2")) {
      settingsFile.append("enableFeaturePreview('VERSION_CATALOGS')\n\n");
    }

    createSettingsFile(settingsFile.append("""
                         dependencyResolutionManagement {
                             versionCatalogs {
                                 create("libs2") {
                                     from(files("gradle/custom.versions.toml"))
                                 }
                             }
                         }
                         """).toString());

    createProjectSubFile("gradle/custom.versions.toml", """
      [libraries]
      jb-annotations = { module = "org.jetbrains:annotations", version = "16.0.2" }
      """);

    importProject();

    final GradleAutoImportAware gradleAutoImportAware = new GradleAutoImportAware();
    assertContain(gradleAutoImportAware.getAffectedExternalProjectFiles(getProjectPath(), myProject),
                  new File(getProjectPath() + "/gradle/custom.versions.toml"));
  }
}

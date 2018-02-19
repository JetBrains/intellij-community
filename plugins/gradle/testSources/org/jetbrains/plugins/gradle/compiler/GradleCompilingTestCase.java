/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.compiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.plugins.gradle.config.GradleResourceCompilerConfigurationGenerator;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;

import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 7/21/2014
 */
public abstract class GradleCompilingTestCase extends GradleImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    final GradleResourceCompilerConfigurationGenerator buildConfigurationGenerator = new GradleResourceCompilerConfigurationGenerator(myProject);
    CompilerManager.getInstance(myProject).addBeforeTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        ApplicationManager.getApplication().runReadAction(() -> buildConfigurationGenerator.generateBuildConfiguration(context));
        return true;
      }
    });
  }

  protected void assertCopied(String path) {
    assertTrue(new File(myProjectConfig.getParent().getPath(), path).exists());
  }

  protected void assertCopied(String path, String content) {
    assertCopied(path);
    assertSameLinesWithFile(new File(myProjectConfig.getParent().getPath(), path).getPath(), content);
  }

  protected void assertNotCopied(String path) {
    assertFalse(new File(myProjectConfig.getParent().getPath(), path).exists());
  }

  @Override
  protected void assertArtifactOutputPath(String artifactName, String expected) {
    final String basePath = getArtifactBaseOutputPath(myProject);
    super.assertArtifactOutputPath(artifactName, basePath + expected);
  }

  protected void assertArtifactOutputPath(Module module, String artifactName, String expected) {
    final String basePath = getArtifactBaseOutputPath(module);
    super.assertArtifactOutputPath(artifactName, basePath + expected);
  }

  protected void assertArtifactOutputFile(String artifactName, String path, String content) {
    final String basePath = getArtifactBaseOutputPath(myProject);
    assertSameLinesWithFile(basePath + path, content);
  }

  protected void assertArtifactOutputFile(String artifactName, String path) {
    final String basePath = getArtifactBaseOutputPath(myProject);
    assertExists(new File(basePath + path));
  }

  private static String getArtifactBaseOutputPath(Project project) {
    String outputUrl = project.getBaseDir().getUrl() + "/out/artifacts";
    return FileUtil.toSystemIndependentName(VfsUtilCore.urlToPath(outputUrl));
  }

  private static String getArtifactBaseOutputPath(Module module) {
    String outputUrl = ExternalSystemApiUtil.getExternalProjectPath(module) + "/build/libs";
    return FileUtil.toSystemIndependentName(VfsUtilCore.urlToPath(outputUrl));
  }
}

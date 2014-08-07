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

import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.util.io.TestFileSystemItem;
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
    CompilerManager.getInstance(myProject).addBeforeTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        AccessToken token = ReadAction.start();
        try {
          new GradleResourceCompilerConfigurationGenerator(myProject, context).generateBuildConfiguration();
        }
        finally {
          token.finish();
        }
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
    final String defaultArtifactOutputPath = ArtifactUtil.getDefaultArtifactOutputPath(artifactName, myProject);
    assert defaultArtifactOutputPath != null;
    final String basePath = FileUtil.toSystemIndependentName(new File(defaultArtifactOutputPath).getParent());
    super.assertArtifactOutputPath(artifactName, basePath + expected);
  }

  protected void assertArtifactOutputFile(String artifactName, String path, String content) {
    final String defaultArtifactOutputPath = ArtifactUtil.getDefaultArtifactOutputPath(artifactName, myProject);
    assert defaultArtifactOutputPath != null;
    final String basePath = FileUtil.toSystemIndependentName(new File(defaultArtifactOutputPath).getParent());
    assertSameLinesWithFile(basePath + path, content);
  }

  protected void assertArtifactOutputFile(String artifactName, String path) {
    final String defaultArtifactOutputPath = ArtifactUtil.getDefaultArtifactOutputPath(artifactName, myProject);
    assert defaultArtifactOutputPath != null;
    final String basePath = FileUtil.toSystemIndependentName(new File(defaultArtifactOutputPath).getParent());
    assertExists(new File(basePath + path));
  }
}

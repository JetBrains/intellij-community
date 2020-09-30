// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vcs.changes.patch.AbstractFilePatchInProgress;
import com.intellij.openapi.vcs.changes.patch.MatchPatchPaths;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.OpenProjectTaskBuilder;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.util.io.PathKt;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class PatchMatcherTest {
  @ClassRule
  public static final ApplicationRule appRule = new ApplicationRule();

  @Rule
  public final TemporaryDirectory tempDir = new TemporaryDirectory();

  @Test
  public void testMatchPathAboveProject() throws IOException {
    Path dir = tempDir.newPath();
    Path projectDir = dir.resolve("project");
    Project project = Objects.requireNonNull(ProjectManagerEx.getInstanceEx().openProject(projectDir, new OpenProjectTaskBuilder().runPostStartUpActivities(false).build()));
    try {
      Path file = dir.resolve("file.txt");
      PathKt.createFile(file);
      TextFilePatch patch = PatchAutoInitTest.create("../file.txt");

      // MatchPatchPaths uses deprecated myProject.getBaseDir() - create and refresh it
      Files.createDirectories(projectDir);
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir);
      MatchPatchPaths iterator = new MatchPatchPaths(project);
      List<AbstractFilePatchInProgress<?>> filePatchInProgresses = iterator.execute(Collections.singletonList(patch));

      assertThat(filePatchInProgresses.size()).isEqualTo(1);
      assertThat(filePatchInProgresses.get(0).getBase().toNioPath()).isEqualTo(file.getParent());
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project);
    }
  }
}

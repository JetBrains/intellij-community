// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.detection;

import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.framework.detection.impl.exclude.ExcludedFileState;
import com.intellij.framework.detection.impl.exclude.ExcludesConfigurationState;
import com.intellij.framework.detection.impl.exclude.old.DisabledAutodetectionByTypeElement;
import com.intellij.framework.detection.impl.exclude.old.DisabledAutodetectionInfo;
import com.intellij.framework.detection.impl.exclude.old.OldFacetDetectionExcludesConfiguration;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class ConvertingOldDetectionExcludesTest extends HeavyPlatformTestCase {
  public static final String FRAMEWORK_ID = "my-framework";

  public void testUseOldConfiguration() {
    final DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    final VirtualFile file = getTempDir().createVirtualFile("my-file.xml");
    state.getElements().add(new DisabledAutodetectionByTypeElement(FRAMEWORK_ID, myModule.getName(), file.getUrl(), false));
    getOldConfiguration().loadState(state);

    assertTrue(isFileExcluded(file));
    assertNull(getNewConfiguration().getState());
    assertOneElement(getOldConfiguration().getState().getElements());
  }

  public void testExcludeModuleConfiguration() throws IOException {
    DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    Path dir = getTempDir().newPath();
    Files.createDirectories(dir);
    VirtualFile virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir);
    PsiTestUtil.addContentRoot(myModule, virtualDir);
    Path file = dir.resolve("my-file.xml");
    Files.createFile(file);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    state.getElements().add(new DisabledAutodetectionByTypeElement(FRAMEWORK_ID, myModule.getName()));
    getOldConfiguration().loadState(state);

    assertTrue(isFileExcluded(virtualFile));
    assertNull(getNewConfiguration().getState());
    assertOneElement(getOldConfiguration().getState().getElements());
  }

  public void testExcludeFrameworkConfiguration() {
    final DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    final VirtualFile file = getTempDir().createVirtualFile("my-file.xml");
    state.getElements().add(new DisabledAutodetectionByTypeElement(FRAMEWORK_ID));
    getOldConfiguration().loadState(state);

    assertTrue(isFileExcluded(file));
    assertNull(getNewConfiguration().getState());
    assertOneElement(getOldConfiguration().getState().getElements());
  }

  public void testUseNewConfiguration() {
    final ExcludesConfigurationState state = new ExcludesConfigurationState();
    final VirtualFile file = getTempDir().createVirtualFile("xxx.xml");
    state.getFiles().add(new ExcludedFileState(file.getUrl(), FRAMEWORK_ID));
    getNewConfiguration().loadState(state);

    assertTrue(isFileExcluded(file));
    assertThat(getOldConfiguration().getState()).isNull();
    final ExcludesConfigurationState newState = getNewConfiguration().getState();
    assertNotNull(newState);
    assertThat(newState.getFiles()).hasSize(1);
  }

  private boolean isFileExcluded(VirtualFile file) {
    return getNewConfiguration().isExcludedFromDetection(file, new MockFrameworkType(FRAMEWORK_ID));
  }

  public void testConvert() {
    final DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    final VirtualFile file = getTempDir().createVirtualFile("my-file.xml");
    state.getElements().add(new DisabledAutodetectionByTypeElement(FRAMEWORK_ID, myModule.getName(), file.getUrl(), false));
    getOldConfiguration().loadState(state);

    getNewConfiguration().addExcludedFramework(new MockFrameworkType("my-framework-2"));
    assertNull(getOldConfiguration().getState());
    final ExcludesConfigurationState newState = getNewConfiguration().getState();
    assertNotNull(newState);
    assertEquals(FRAMEWORK_ID, assertOneElement(newState.getFiles()).getFrameworkType());
    assertEquals("my-framework-2", assertOneElement(newState.getFrameworkTypes()));
  }

  private DetectionExcludesConfigurationImpl getNewConfiguration() {
    return ((DetectionExcludesConfigurationImpl)DetectionExcludesConfiguration.getInstance(myProject));
  }

  private OldFacetDetectionExcludesConfiguration getOldConfiguration() {
    return OldFacetDetectionExcludesConfiguration.getInstance(myProject);
  }
}

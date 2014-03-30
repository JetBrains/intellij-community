/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection;

import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.framework.detection.impl.exclude.ExcludedFileState;
import com.intellij.framework.detection.impl.exclude.ExcludesConfigurationState;
import com.intellij.framework.detection.impl.exclude.old.DisabledAutodetectionByTypeElement;
import com.intellij.framework.detection.impl.exclude.old.DisabledAutodetectionInfo;
import com.intellij.framework.detection.impl.exclude.old.OldFacetDetectionExcludesConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TempFiles;

/**
 * @author nik
 */
public class ConvertingOldDetectionExcludesTest extends PlatformTestCase {
  public static final String FRAMEWORK_ID = "my-framework";
  private TempFiles myTempFiles;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempFiles = new TempFiles(myFilesToDelete);
  }

  public void testUseOldConfiguration() {
    final DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    final VirtualFile file = myTempFiles.createVFile("my-file", ".xml");
    state.getElements().add(new DisabledAutodetectionByTypeElement(FRAMEWORK_ID, myModule.getName(), file.getUrl(), false));
    getOldConfiguration().loadState(state);

    assertTrue(isFileExcluded(file));
    assertNull(getNewConfiguration().getState());
    assertOneElement(getOldConfiguration().getState().getElements());
  }

  public void testExcludeModuleConfiguration() {
    final DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    final VirtualFile dir = myTempFiles.createTempVDir();
    PsiTestUtil.addContentRoot(myModule, dir);
    final VirtualFile file = myTempFiles.createVFile(dir, "my-file", ".xml");
    state.getElements().add(new DisabledAutodetectionByTypeElement(FRAMEWORK_ID, myModule.getName()));
    getOldConfiguration().loadState(state);

    assertTrue(isFileExcluded(file));
    assertNull(getNewConfiguration().getState());
    assertOneElement(getOldConfiguration().getState().getElements());
  }

  public void testExcludeFrameworkConfiguration() {
    final DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    final VirtualFile file = myTempFiles.createVFile("my-file", ".xml");
    state.getElements().add(new DisabledAutodetectionByTypeElement(FRAMEWORK_ID));
    getOldConfiguration().loadState(state);

    assertTrue(isFileExcluded(file));
    assertNull(getNewConfiguration().getState());
    assertOneElement(getOldConfiguration().getState().getElements());
  }

  public void testUseNewConfiguration() {
    final ExcludesConfigurationState state = new ExcludesConfigurationState();
    final VirtualFile file = myTempFiles.createVFile("xxx", ".xml");
    state.getFiles().add(new ExcludedFileState(file.getUrl(), FRAMEWORK_ID));
    getNewConfiguration().loadState(state);

    assertTrue(isFileExcluded(file));
    assertNull(getOldConfiguration().getState());
    final ExcludesConfigurationState newState = getNewConfiguration().getState();
    assertNotNull(newState);
    assertOneElement(newState.getFiles());
  }

  private boolean isFileExcluded(VirtualFile file) {
    return getNewConfiguration().isExcludedFromDetection(file, new MockFrameworkType(FRAMEWORK_ID));
  }

  public void testConvert() {
    final DisabledAutodetectionInfo state = new DisabledAutodetectionInfo();
    final VirtualFile file = myTempFiles.createVFile("my-file", ".xml");
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

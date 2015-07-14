/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class ProjectStoreImplIdeaDirTest extends ProjectStoreBaseTestCase {
  @Override
  protected File getIprFile() throws IOException {
    final File projectDir = FileUtil.createTempDirectory(getTestName(true), "project");
    File ideaDir = new File(projectDir, Project.DIRECTORY_STORE_FOLDER);
    assertTrue(ideaDir.mkdir() || ideaDir.isDirectory());
    File iprFile = new File(ideaDir, "misc.xml");
    FileUtil.writeToFile(iprFile, getIprFileContent());

    myFilesToDelete.add(projectDir);
    return projectDir;
  }

  public void testLoadFromDirectoryStorage() throws Exception {
    ((ProjectEx)myProject).setOptimiseTestLoadSpeed(false);

    final TestIprComponent testIprComponent = new TestIprComponent();
    ((ProjectEx)myProject).getStateStore().initComponent(testIprComponent, false);
    assertNotNull(testIprComponent.myState);
  }
}

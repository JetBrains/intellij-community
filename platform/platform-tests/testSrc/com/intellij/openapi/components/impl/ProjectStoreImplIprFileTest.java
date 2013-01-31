/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class ProjectStoreImplIprFileTest extends ProjectStoreBaseTestCase {
  @Override
  protected File getIprFile() throws IOException {
    final File iprFile = super.getIprFile();
    FileUtil.writeToFile(iprFile, getIprFileContent());
    return iprFile;
  }

  public void testLoadFromOldStorage() throws Exception {
    final IProjectStore projectStore = ((ProjectEx)myProject).getStateStore();

    ((ProjectEx)myProject).setOptimiseTestLoadSpeed(false);

    final TestIprComponent testIprComponent = new TestIprComponent();
    projectStore.initComponent(testIprComponent, false);
    assertNotNull(testIprComponent.myState);
  }
}

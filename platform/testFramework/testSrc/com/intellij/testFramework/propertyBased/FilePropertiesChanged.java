/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class FilePropertiesChanged implements MadTestingAction {
  private final VirtualFile myFile;
  private final Project myProject;

  public FilePropertiesChanged(PsiFile file) {
    myFile = file.getVirtualFile();
    myProject = file.getProject();
  }

  @Override
  public void performAction() {
    PushedFilePropertiesUpdater.getInstance(myProject).filePropertiesChanged(myFile, Conditions.alwaysTrue());
  }

  @Override
  public String toString() {
    return "FilePropertiesChanged[" + myFile.getPath() + ']';
  }
}

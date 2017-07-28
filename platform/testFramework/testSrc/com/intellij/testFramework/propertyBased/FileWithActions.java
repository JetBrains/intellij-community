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

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.RunAll;

import java.util.List;

/**
 * @author peter
 */
public class FileWithActions {
  private final PsiFile myFile;
  private final List<? extends MadTestingAction> myActions;

  public FileWithActions(PsiFile file, List<? extends MadTestingAction> actions) {
    myFile = file;
    myActions = actions;
  }

  public PsiFile getPsiFile() {
    return myFile;
  }

  @Override
  public String toString() {
    return myFile.getVirtualFile().getPath() + "[" + StringUtil.join(myActions, a -> "\n  " + a, "") + "\n]";
  }

  public boolean runActions() {
    Project project = myFile.getProject();
    new RunAll(() -> MadTestingUtil.changeAndRevert(project, () -> MadTestingAction.runActions(myActions)),
               () -> WriteAction.run(() -> myFile.getVirtualFile().delete(this))).run();
    return true;
  }

}

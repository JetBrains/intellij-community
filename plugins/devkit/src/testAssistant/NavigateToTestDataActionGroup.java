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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NavigateToTestDataActionGroup extends ActionGroup {
  private final PsiMethod myMethod;

  public NavigateToTestDataActionGroup(PsiMethod method) {
    myMethod = method;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    List<String> names = TestDataNavigationHandler.getFileNames(myMethod);
    if (names == null || names.isEmpty()) return new AnAction[0];
    return ContainerUtil.map2Array(names, AnAction.class, new Function<String, AnAction>() {
      @Override
      public AnAction fun(String s) {
        return new GotoTestDataAction(s, myMethod.getProject(), FileTypeManager.getInstance().getFileTypeByFileName(s).getIcon());
      }
    });
  }
}

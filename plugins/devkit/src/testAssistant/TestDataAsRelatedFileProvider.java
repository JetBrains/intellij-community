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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 5/24/11 4:52 PM
 */
public class TestDataAsRelatedFileProvider extends GotoRelatedProvider {

  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final Editor editor = context.getData(CommonDataKeys.EDITOR);
    final Project project = context.getData(CommonDataKeys.PROJECT);
    final PsiElement element = context.getData(CommonDataKeys.PSI_ELEMENT);
    if (editor == null || element == null || project == null) {
      return Collections.emptyList();
    }

    PsiMethod method = UastContextKt.getUastParentOfType(element, UMethod.class);
    if (method == null) {
      return Collections.emptyList();
    }

    final List<String> testDataFiles = NavigateToTestDataAction.findTestDataFiles(context);
    if (testDataFiles == null || testDataFiles.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new TestDataRelatedItem(method, editor, testDataFiles));
  }
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 5/24/11 4:59 PM
 */
public class TestDataRelatedItem extends GotoRelatedItem{
  
  private final List<String> myTestDataFiles = new ArrayList<>();
  private final Editor myEditor;
  private final PsiMethod myMethod;

  public TestDataRelatedItem(@NotNull PsiMethod method, @NotNull Editor editor, @NotNull Collection<String> testDataFiles) {
    super(method, "Test Data");
    myMethod = method;
    myEditor = editor;
    myTestDataFiles.addAll(testDataFiles);
  }

  @Override
  public String getCustomName() {
    if (myTestDataFiles.size() != 1) {
      return "test data";
    }
    return PathUtil.getFileName(myTestDataFiles.get(0));
  }

  @Override
  public void navigate() {
    TestDataNavigationHandler.navigate(JBPopupFactory.getInstance().guessBestPopupLocation(myEditor), myTestDataFiles,
                                       myMethod.getProject());
  }
}

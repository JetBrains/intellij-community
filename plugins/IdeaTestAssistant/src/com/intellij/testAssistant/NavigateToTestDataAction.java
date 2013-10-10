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
package com.intellij.testAssistant;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class NavigateToTestDataAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiMethod method = findTargetMethod(e.getDataContext());
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (method == null || editor == null) {
      return;
    }
    List<String> fileNames = findTestDataFiles(e.getDataContext());
    if (fileNames == null || fileNames.isEmpty()) {
      String message = "Cannot find testdata files for class";
      final Notification notification = new Notification("testdata", "Found no testdata files", message, NotificationType.INFORMATION);
      Notifications.Bus.notify(notification, method.getProject());
    }
    else {
      TestDataNavigationHandler.navigate(method, JBPopupFactory.getInstance().guessBestPopupLocation(editor), fileNames);
    }
  }

  @Nullable
  public static List<String> findTestDataFiles(@NotNull DataContext context) {
    final PsiMethod method = findTargetMethod(context);
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    if (method == null || editor == null) {
      return null;
    }
    final String name = method.getName();


    String testDataPath = null;
    if (name.startsWith("test")) {
      testDataPath = TestDataLineMarkerProvider.getTestDataBasePath(method.getContainingClass());
    }
    final TestDataReferenceCollector collector = new TestDataReferenceCollector(testDataPath, name.substring(4));
    return collector.collectTestDataReferences(method);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(findTargetMethod(e.getDataContext()) != null);
  }

  @Nullable
  private static PsiMethod findTargetMethod(@NotNull DataContext context) {
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    if (file != null && editor != null) {
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }
    return null;
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.Location;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.testframework.TestTreeViewAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.Parameterized;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class NavigateToTestDataAction extends AnAction implements TestTreeViewAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = e.getProject();
    if (project == null) return;
    final Editor editor = e.getData(CommonDataKeys.EDITOR);

    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    final RelativePoint point = editor != null ? popupFactory.guessBestPopupLocation(editor) :
                                popupFactory.guessBestPopupLocation(dataContext);

    List<TestDataFile> fileNames = findTestDataFiles(dataContext, project, true);
    if (fileNames.isEmpty()) {
      Notification notification = new Notification(
        "testdata",
        "Found no test data files",
        "Cannot find test data files for class",
        NotificationType.INFORMATION);
      Notifications.Bus.notify(notification, project);
    } else {
      TestDataNavigationHandler.navigate(point, fileNames, project);
    }
  }

  @NotNull
  static List<TestDataFile> findTestDataFiles(@NotNull DataContext dataContext, @NotNull Project project, boolean shouldGuess) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      List<TestDataFile> fileNames = tryFindTestDataFiles(dataContext);
      if (fileNames.isEmpty() && shouldGuess) {
        //noinspection RedundantTypeArguments
        return ReadAction.<List<TestDataFile>, RuntimeException>compute(() -> {
          PsiMethod method = findTargetMethod(dataContext);
          return method == null ? Collections.emptyList() : TestDataGuessByExistingFilesUtil.guessTestDataName(method);
        });
      }
      return Collections.emptyList();
    }, DevKitBundle.message("testdata.searching"), true, project);
  }

  @NotNull
  private static List<TestDataFile> tryFindTestDataFiles(@NotNull DataContext context) {
    final PsiMethod method = ReadAction.compute(() -> findTargetMethod(context));
    if (method == null) {
      PsiClass parametrizedTestClass = ReadAction.compute(() -> findParametrizedClass(context));
      return parametrizedTestClass == null ? Collections.emptyList() : TestDataGuessByTestDiscoveryUtil.collectTestDataByExistingFiles(parametrizedTestClass);
    }
    final String name = ReadAction.compute(() -> method.getName());

    if (name.startsWith("test")) {
      String testDataPath = ReadAction.compute(() -> TestDataLineMarkerProvider.getTestDataBasePath(method.getContainingClass()));
      final TestDataReferenceCollector collector = new TestDataReferenceCollector(testDataPath, name.substring(4));
      return collector.collectTestDataReferences(method);
    }

    return ReadAction.compute(() -> {
      final Location<?> location = Location.DATA_KEY.getData(context);
      if (location instanceof PsiMemberParameterizedLocation) {
        PsiClass parametrizedTestClass = findParametrizedClass(context);
        if (parametrizedTestClass != null) {
          String testDataPath = TestDataLineMarkerProvider.getTestDataBasePath(parametrizedTestClass);
          String paramSetName = ((PsiMemberParameterizedLocation)location).getParamSetName();
          String baseFileName = StringUtil.trimEnd(StringUtil.trimStart(paramSetName, "["), "]");
          return TestDataGuessByExistingFilesUtil.suggestTestDataFiles(baseFileName, testDataPath, parametrizedTestClass);
        }
      }
      return Collections.emptyList();
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(findTargetMethod(e.getDataContext()) != null || findParametrizedClass(e.getDataContext()) != null);
  }

  @Nullable
  static PsiClass findParametrizedClass(@NotNull DataContext context) {
    PsiElement element = context.getData(CommonDataKeys.PSI_ELEMENT);
    UClass uClass = UastContextKt.getUastParentOfType(element, UClass.class);
    if (uClass == null) return null;
    final UAnnotation annotation = UastContextKt.toUElement(AnnotationUtil.findAnnotationInHierarchy(uClass.getJavaPsi(), Collections.singleton(JUnitUtil.RUN_WITH)), UAnnotation.class);
    if (annotation == null) return null;
    UExpression value = annotation.findAttributeValue("value");
    if (!(value instanceof UClassLiteralExpression)) return null;
    UClassLiteralExpression classLiteralExpression = (UClassLiteralExpression)value;
    PsiType type = classLiteralExpression.getType();
    return type != null && type.equalsToText(Parameterized.class.getName()) ? uClass.getJavaPsi() : null;
  }

  @Nullable
  private static PsiMethod findTargetMethod(@NotNull DataContext context) {
    final Location<?> location = Location.DATA_KEY.getData(context);
    if (location != null) {
      final PsiElement element = location.getPsiElement();
      PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
      if (method != null) {
        return method;
      }
    }
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    if (file != null && editor != null) {
      return UastContextKt.findUElementAt(file, editor.getCaretModel().getOffset(), UMethod.class);
    }

    return null;
  }
}

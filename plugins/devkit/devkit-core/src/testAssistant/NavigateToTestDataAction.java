// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.Location;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.testframework.TestTreeViewAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;

import java.util.Collections;
import java.util.List;

public final class NavigateToTestDataAction extends AnAction implements TestTreeViewAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

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
        DevKitBundle.message("testdata.notification.no.test.datafiles.title"),
        DevKitBundle.message("testdata.notification.no.test.datafiles.content"),
        NotificationType.INFORMATION);
      Notifications.Bus.notify(notification, project);
    } else {
      TestDataNavigationHandler.navigate(point, fileNames, project);
    }
  }

  @VisibleForTesting
  public static @NotNull List<TestDataFile> findTestDataFiles(@NotNull DataContext dataContext, @NotNull Project project, boolean shouldGuess) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      List<TestDataFile> fileNames = tryFindTestDataFiles(dataContext);
      if (fileNames.isEmpty() && shouldGuess) {
        //noinspection RedundantTypeArguments
        return ReadAction.<List<TestDataFile>, RuntimeException>compute(() -> {
          PsiMethod method = findTargetMethod(dataContext);
          return method == null ? Collections.emptyList() : TestDataGuessByExistingFilesUtil.guessTestDataName(method);
        });
      }
      return fileNames;
    }, DevKitBundle.message("testdata.searching"), true, project);
  }

  private static @NotNull List<TestDataFile> tryFindTestDataFiles(@NotNull DataContext context) {
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

    List<TestDataFile> result = ReadAction.compute(() -> {
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

    if (result.isEmpty()) {
      String testDataPath = ReadAction.compute(() -> TestDataLineMarkerProvider.getTestDataBasePath(method.getContainingClass()));
      final TestDataReferenceCollector collector = new TestDataReferenceCollector(testDataPath, name);
      return collector.collectTestDataReferences(method);
    } else {
      return result;
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(findTargetMethod(e.getDataContext()) != null || findParametrizedClass(e.getDataContext()) != null);
  }

  static @Nullable PsiClass findParametrizedClass(@NotNull DataContext context) {
    PsiElement element = context.getData(CommonDataKeys.PSI_ELEMENT);
    UClass uClass = UastContextKt.getUastParentOfType(element, UClass.class);
    if (uClass == null) return null;
    final UAnnotation annotation = UastContextKt.toUElement(AnnotationUtil.findAnnotationInHierarchy(uClass.getJavaPsi(), Collections.singleton(JUnitUtil.RUN_WITH)), UAnnotation.class);
    if (annotation == null) return null;
    UExpression value = annotation.findAttributeValue("value");
    if (!(value instanceof UClassLiteralExpression classLiteralExpression)) return null;
    PsiType type = classLiteralExpression.getType();
    return type != null && type.equalsToText(TestFrameworkConstants.PARAMETERIZED_ANNOTATION_QUALIFIED_NAME) ? uClass.getJavaPsi() : null;
  }

  private static @Nullable PsiMethod findTargetMethod(@NotNull DataContext context) {
    final Location<?> location = Location.DATA_KEY.getData(context);
    if (location != null) {
      UMethod method = UastContextKt.getUastParentOfType(location.getPsiElement(), UMethod.class, false);
      if (method != null) {
        return method.getJavaPsi();
      }
    }
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    if (file != null && editor != null) {
      UMethod method = UastContextKt.findUElementAt(file, editor.getCaretModel().getOffset(), UMethod.class);
      return method != null ? method.getJavaPsi() : null;
    }

    return null;
  }
}

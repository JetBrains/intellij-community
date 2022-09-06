// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.i18n.batch.I18nizeMultipleStringsDialog;
import com.intellij.codeInspection.i18n.batch.I18nizedPropertyData;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.ResourceBundleManager;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PartiallyKnownString;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

import java.util.*;

public class I18nizeBatchQuickFix extends I18nizeQuickFix implements BatchQuickFix {
  private static final Logger LOG = Logger.getInstance(I18nizeBatchQuickFix.class);


  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {

    final Map<String, I18nizedPropertyData<HardcodedStringContextData>> keyValuePairs = new LinkedHashMap<>();
    final Set<PsiFile> contextFiles = new LinkedHashSet<>();
    ReadAction
      .nonBlocking(() -> {
        fillI18nizedPropertyDataMap(project, descriptors, contextFiles, keyValuePairs);
        if (keyValuePairs.isEmpty()) return null;
        return I18nizeMultipleStringsDialog.getResourceBundleManager(project, contextFiles);
      })
      .finishOnUiThread(ModalityState.NON_MODAL, bundleManager -> {
        if (keyValuePairs.isEmpty()) return;
        showI18nizeMultipleStringsDialog(project, keyValuePairs, contextFiles, bundleManager);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static void fillI18nizedPropertyDataMap(@NotNull Project project,
                                                  CommonProblemDescriptor @NotNull [] descriptors,
                                                  @NotNull Set<PsiFile> contextFiles,
                                                  @NotNull Map<String, I18nizedPropertyData<HardcodedStringContextData>> keyValuePairs) {
    final Set<PsiElement> distinct = new HashSet<>();
    final UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();

    for (CommonProblemDescriptor descriptor : descriptors) {
      PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      UPolyadicExpression polyadicExpression = I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(psiElement);
      UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(
        polyadicExpression != null ? polyadicExpression : UastContextKt.toUElement(psiElement, UInjectionHost.class)
      );
      if (concatenation != null) {
        PartiallyKnownString pks = concatenation.asPartiallyKnownString();
        if (pks.getSegments().size() == 1) {
          String value = pks.getValueIfKnown();
          if (distinct.add(psiElement) && value != null) {
            I18nizedPropertyData<HardcodedStringContextData> data = keyValuePairs.get(value);
            if (data != null) {
              data.contextData().getPsiElements().add(psiElement);
              data.contextData().getExpressions().add(concatenation.getRootUExpression());
            }
            else {
              String key = ObjectUtils.notNull(suggestKeyByPlace(value, concatenation.getRootUExpression()),
                                               I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null));
              ArrayList<PsiElement> elements = new ArrayList<>();
              elements.add(psiElement);
              List<UExpression> uExpressions = new ArrayList<>();
              uExpressions.add(concatenation.getRootUExpression());
              HardcodedStringContextData contextData = new HardcodedStringContextData(uExpressions, elements, Collections.emptyList());
              keyValuePairs.put(value, new I18nizedPropertyData<>(uniqueNameGenerator.generateUniqueName(key), value, contextData));
            }
            ContainerUtil.addIfNotNull(contextFiles, psiElement.getContainingFile());
          }
        }
        else if (distinct.add(concatenation.getRootUExpression().getSourcePsi())) {
          ArrayList<UExpression> args = new ArrayList<>();
          String value = JavaI18nUtil.buildUnescapedFormatString(concatenation, args, project);
          String key = ObjectUtils.notNull(suggestKeyByPlace(value, concatenation.getRootUExpression()),
                                           I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null));
          HardcodedStringContextData contextData = new HardcodedStringContextData(
            Collections.singletonList(concatenation.getRootUExpression()),
            Collections.singletonList(concatenation.getRootUExpression().getSourcePsi()),
            args);
          keyValuePairs.put(value + concatenation.hashCode(),
                            new I18nizedPropertyData<>(uniqueNameGenerator.generateUniqueName(key), value, contextData));
          ContainerUtil.addIfNotNull(contextFiles, psiElement.getContainingFile());
        }
      }
    }
  }

  private void showI18nizeMultipleStringsDialog(@NotNull Project project,
                         @NotNull Map<String, I18nizedPropertyData<HardcodedStringContextData>> keyValuePairs,
                         @NotNull Set<PsiFile> contextFiles,
                         @Nullable ResourceBundleManager bundleManager) {
    ArrayList<I18nizedPropertyData<HardcodedStringContextData>> replacements = new ArrayList<>(keyValuePairs.values());
    I18nizeMultipleStringsDialog<HardcodedStringContextData> dialog =
      new I18nizeMultipleStringsDialog<>(project, replacements, contextFiles, bundleManager,
                                         data -> {
                                           List<PsiElement> elements = data.getPsiElements();
                                           return ContainerUtil.map(elements, element -> new UsageInfo(element.getParent()));
                                         },
                                         null, true);

    if (dialog.showAndGet()) {
      PropertiesFile propertiesFile = dialog.getPropertiesFile();
      Set<PsiFile> files = new HashSet<>();
      for (I18nizedPropertyData<HardcodedStringContextData> data : replacements) {
        for (PsiElement element : data.contextData().getPsiElements()) {
          ContainerUtil.addIfNotNull(files, element.getContainingFile());
        }
      }
      if (files.isEmpty()) {
        return;
      }
      files.add(propertiesFile.getContainingFile());

      WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
        for (I18nizedPropertyData<HardcodedStringContextData> data : replacements) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                        Collections.singletonList(propertiesFile),
                                                                        data.key(),
                                                                        data.value(),
                                                                        new UExpression[0]);
          List<UExpression> uExpressions = data.contextData().getExpressions();
          List<PsiElement> psiElements = data.contextData().getPsiElements();
          for (int i = 0; i < psiElements.size(); i++) {
            PsiElement psiElement = psiElements.get(i);
            UExpression uExpression = uExpressions.get(i);
            Language language = psiElement.getLanguage();
            String i18NText =
              dialog.getI18NText(data.key(), data.value(), JavaI18nUtil.composeParametersText(data.contextData().getArgs()));

            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
            PsiExpression expression;
            try {
              expression = elementFactory.createExpressionFromText(i18NText, psiElement);
              if (language == JavaLanguage.INSTANCE) {
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiElement.replace(expression));
                continue;
              }
            }
            catch (IncorrectOperationException e) {
              LOG.debug(e);
              try {
                expression = elementFactory.createExpressionFromText(dialog.getI18NText(data.key(), data.value(), ""), psiElement);
              }
              catch (IncorrectOperationException exception) {
                continue;
              }
            }

            @Nullable Couple<String> callDescriptor = getCallDescriptor(expression);
            if (callDescriptor == null) {
              LOG.debug("Templates are not supported for " + language.getDisplayName());
              continue;
            }


            UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(language);
            if (generationPlugin == null) {
              LOG.debug("No UAST generation plugin exist for " + language.getDisplayName());
              continue;
            }
            UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
            List<UExpression> arguments = new ArrayList<>();
            arguments.add(pluginElementFactory.createStringLiteralExpression(data.key(), psiElement));
            arguments.addAll(data.contextData().getArgs());

            UExpression receiver = callDescriptor.first != null
                                   ? pluginElementFactory.createQualifiedReference(callDescriptor.first, uExpression.getSourcePsi())
                                   : null;
            UCallExpression callExpression = pluginElementFactory
              .createCallExpression(receiver,
                                    callDescriptor.second,
                                    arguments,
                                    null,
                                    UastCallKind.METHOD_CALL,
                                    psiElement);
            if (callExpression != null) {
              generationPlugin.replace(uExpression, callExpression, UCallExpression.class);
            }
            else {
              LOG.debug("Null generated UAST call expression");
            }
          }
        }
      }, files.toArray(PsiFile.EMPTY_ARRAY));
    }
  }

  /**
   * @return qualifier.methodName couple
   */
  private static @Nullable Couple<String> getCallDescriptor(PsiExpression expression) {
    if (expression instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      String qualifiedName = qualifierExpression != null ? qualifierExpression.getText() : null;
      String methodName = methodExpression.getReferenceName();
      if (methodName == null) return null;
      
      PsiExpression[] expressions = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions();
      if (expressions.length == 0) {
        return null;
      }
      if (!(expressions[0] instanceof PsiLiteralExpression)) {
        return null;
      }
      return Couple.of(qualifiedName, methodName);
    }
    return null;
  }

  @Nullable
  private static String suggestKeyByPlace(String value, @NotNull UExpression expression) {
    List<UExpression> usages = I18nInspection.findIndirectUsages(expression, true);
    if (usages.isEmpty()) {
      usages = Collections.singletonList(expression);
    }
    for (UExpression usage : usages) {
      NlsInfo nlsInfo = NlsInfo.forExpression(usage);
      if (nlsInfo instanceof NlsInfo.Localized) {
        return I18nizeQuickFix.getSuggestedName(value, ((NlsInfo.Localized)nlsInfo));
      }
    }
    return null;
  }

  private static final class HardcodedStringContextData {
    private final List<UExpression> myExpressions;
    private final List<PsiElement> myPsiElements;
    private final List<UExpression> myArgs;

    private HardcodedStringContextData(@NotNull List<UExpression> expressions, @NotNull List<PsiElement> psiElements,
                                       @NotNull List<UExpression> args) {
      myExpressions = expressions;
      myPsiElements = psiElements;
      myArgs = args;
    }

    private List<UExpression> getExpressions() {
      return myExpressions;
    }

    private List<PsiElement> getPsiElements() {
      return myPsiElements;
    }

    private List<UExpression> getArgs() {
      return myArgs;
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.i18n.batch.I18nizeMultipleStringsDialog;
import com.intellij.codeInspection.i18n.batch.I18nizedPropertyData;
import com.intellij.lang.Language;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PartiallyKnownString;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import com.intellij.util.text.UniqueNameGenerator;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;

import java.util.*;

/**
 * WARNING
 * Templates are ignored BundleName.message(key, args) is always used instead
 */
public class I18nizeBatchQuickFix extends I18nizeQuickFix implements BatchQuickFix<CommonProblemDescriptor> {
  private static final Logger LOG = Logger.getInstance(I18nizeBatchQuickFix.class);


  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    Set<PsiElement> distinct = new HashSet<>();
    Map<String, I18nizedPropertyData<HardcodedStringContextData>> keyValuePairs = new LinkedHashMap<>();
    UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();
    Set<PsiFile> contextFiles = new LinkedHashSet<>();
    for (CommonProblemDescriptor descriptor : descriptors) {
      PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(
        UastContextKt.toUElement(psiElement, UInjectionHost.class)
      );
      if (concatenation != null) {
        PartiallyKnownString pks = concatenation.asPartiallyKnownString();
        if (pks.getSegments().size() == 1) {
          String value = pks.getValueIfKnown();
          if (distinct.add(psiElement) && value != null) {
            I18nizedPropertyData<HardcodedStringContextData> data = keyValuePairs.get(value);
            if (data != null) {
              data.getContextData().getPsiElements().add(psiElement);
              data.getContextData().getExpressions().add(concatenation.getRootUExpression());
            }
            else {
              String key = ObjectUtils.notNull(suggestKeyByPlace(concatenation.getRootUExpression()),
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
          String value = buildUnescapedFormatString(concatenation, args);
          String key = ObjectUtils.notNull(suggestKeyByPlace(concatenation.getRootUExpression()),
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

    if (keyValuePairs.isEmpty()) return;

    ArrayList<I18nizedPropertyData<HardcodedStringContextData>> replacements = new ArrayList<>(keyValuePairs.values());
    I18nizeMultipleStringsDialog dialog = new I18nizeMultipleStringsDialog<>(project, replacements, contextFiles, data -> {
      List<PsiElement> elements = data.getPsiElements();
      return ContainerUtil.map(elements, element -> new UsageInfo(element.getParent()));
    }, null);
    if (dialog.showAndGet()) {
      PropertiesFile propertiesFile = dialog.getPropertiesFile();
      Set<PsiFile> files = new HashSet<>();
      for (I18nizedPropertyData<HardcodedStringContextData> pair : replacements) {
        for (PsiElement element : pair.getContextData().getPsiElements()) {
          ContainerUtil.addIfNotNull(files, element.getContainingFile());
        }
      }
      if (files.isEmpty()) {
        return;
      }
      files.add(propertiesFile.getContainingFile());

      WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
        String bundleName = propertiesFile.getVirtualFile().getNameWithoutExtension();
        PsiClass[] classesByName = PsiShortNamesCache.getInstance(project).getClassesByName(bundleName,
                                                                                            GlobalSearchScope.projectScope(project));
        if (classesByName.length == 1) {
          bundleName = classesByName[0].getQualifiedName();
          LOG.assertTrue(bundleName != null, propertiesFile.getName());
        }
        for (I18nizedPropertyData<HardcodedStringContextData> data : replacements) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                        Collections.singletonList(propertiesFile),
                                                                        data.getKey(),
                                                                        data.getValue(),
                                                                        PsiExpression.EMPTY_ARRAY);
          List<UExpression> uExpressions = data.getContextData().getExpressions();
          List<PsiElement> psiElements = data.getContextData().getPsiElements();
          for (int i = 0; i < psiElements.size(); i++) {
            PsiElement psiElement = psiElements.get(i);
            UExpression uExpression = uExpressions.get(i);
            Language language = psiElement.getLanguage();
            UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(language);
            if (generationPlugin == null) {
              LOG.debug("No UAST generation plugin exist for " + language.getDisplayName());
              continue;
            }
            UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
            List<UExpression> arguments = new ArrayList<>();
            arguments.add(pluginElementFactory.createStringLiteralExpression(data.getKey(), psiElement));
            arguments.addAll(data.getContextData().getArgs());
            UCallExpression callExpression = pluginElementFactory
              .createCallExpression(pluginElementFactory.createQualifiedReference(bundleName, uExpression),
                                    "message",
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

  private static String buildUnescapedFormatString(UStringConcatenationsFacade cf, List<? super UExpression> formatParameters) {
    StringBuilder result = new StringBuilder();
    int elIndex = 0;
    for (UExpression expression : SequencesKt.asIterable(cf.getUastOperands())) {
      if (expression instanceof ULiteralExpression) {
        Object value = ((ULiteralExpression)expression).getValue();
        if (value != null) {
          result.append(PsiConcatenationUtil.formatString(value.toString(), false));
        }
      }
      else {
        result.append("{").append(elIndex++).append("}");
        formatParameters.add(expression);
      }
    }
    return result.toString();
  }

  /**
   * If expression is passed to ProblemsHolder#registerProblem, suggest inspection.class.name.description key
   * If expression is returned from getName/getFamilyName of the LocalQuickFix, suggest quick.fix.text/family.name
   */
  @Nullable
  private static String suggestKeyByPlace(@NotNull UExpression expression) {
    UElement parent = UastUtils.skipParenthesizedExprUp(expression.getUastParent());
    if (parent instanceof UPolyadicExpression) {
      parent = UastUtils.skipParenthesizedExprUp(parent.getUastParent());
    }
    if (parent == null) return null;
    UCallExpression callExpression = UastUtils.getUCallExpression(parent);
    if (callExpression != null) {
      PsiMethod method = callExpression.resolve();
      if (method != null) {
        if ("registerProblem".equals(method.getName()) &&
            InheritanceUtil.isInheritor(method.getContainingClass(), ProblemsHolder.class.getName())) {
          PsiClass containingClass = PsiTreeUtil.getParentOfType(callExpression.getSourcePsi(), PsiClass.class);
          while (containingClass != null) {
            if (InheritanceUtil.isInheritor(containingClass, InspectionProfileEntry.class.getName())) {
              String containingClassName = containingClass.getName();
              return containingClassName == null
                     ? null
                     : "inspection." + toPropertyName(InspectionProfileEntry.getShortName(containingClassName)) + ".description";
            }
            containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, true);
          }
        }
      }
      return null;
    }

    final UElement returnStmt =
      UastUtils.getParentOfType(parent, UReturnExpression.class, false, UCallExpression.class, ULambdaExpression.class);
    if (returnStmt instanceof UReturnExpression) {
      UMethod uMethod = UastUtils.getParentOfType(expression, UMethod.class);
      if (uMethod != null) {
        UElement uClass = uMethod.getUastParent();
        if (uClass instanceof UClass && InheritanceUtil.isInheritor(((UClass)uClass), LocalQuickFix.class.getName())) {
          String name = ((UClass)uClass).getName();
          if (name != null) {
            if ("getName".equals(uMethod.getName())) {
              return toPropertyName(name) + ".text";
            }
            if ("getFamilyName".equals(uMethod.getName())) {
              return toPropertyName(name) + ".family.name";
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static String toPropertyName(String name) {
    return StringUtil.join(NameUtilCore.splitNameIntoWords(name), s -> StringUtil.decapitalize(s), ".");
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

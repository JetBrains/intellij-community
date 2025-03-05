// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public final class DisabledConditionUtil {

  private static final String[] DISABLED_ANNO = {"org.junit.jupiter.api.Disabled"};

  private static final String[] DISABLED_COND_ANNO = {
    "org.junit.jupiter.api.condition.DisabledOnJre",
    "org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable",
    "org.junit.jupiter.api.condition.DisabledIfSystemProperty",
    "org.junit.jupiter.api.condition.DisabledOnOs"
  };

  private static final String[] SCRIPT_COND_ANNO =
    {
      "org.junit.jupiter.api.condition.DisabledIf",
      "org.junit.jupiter.api.condition.EnabledIf"
    };

  private static final String[] ENABLED_COND_ANNO = {
    "org.junit.jupiter.api.condition.EnabledOnJre",
    "org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable",
    "org.junit.jupiter.api.condition.EnabledIfSystemProperty",
    "org.junit.jupiter.api.condition.EnabledOnOs"
  };

  public static @Nullable String getDisabledConditionValue(JUnitConfiguration configuration) {
    JUnitConfiguration.Data data = configuration.getPersistentData();
    if (data == null) return null;

    final GlobalSearchScope globalSearchScope = TestObject.getScopeForJUnit(configuration);
    boolean isMethodConfiguration = JUnitConfiguration.TEST_METHOD.equals(data.TEST_OBJECT);
    boolean isClassConfiguration = JUnitConfiguration.TEST_CLASS.equals(data.TEST_OBJECT);
    final PsiClass psiClass = isMethodConfiguration || isClassConfiguration
                              ? JavaExecutionUtil.findMainClass(configuration.getProject(), data.getMainClassName(), globalSearchScope)
                              : null;

    if (psiClass == null) return null;
    String disabledCondition = getDisabledCondition(psiClass);
    if (disabledCondition != null) {
      return disabledCondition;
    }
    String methodName = data.getMethodName();
    if (methodName != null) {
      final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(psiClass);
      String currentSignature = data.getMethodNameWithSignature();
      for (PsiMethod t : psiClass.findMethodsByName(methodName, true)) {
        if (filter.value(t) && Objects.equals(currentSignature, JUnitConfiguration.Data.getMethodPresentation(t))) {
          return getDisabledCondition(t);
        }
      }
    }
    return null;
  }

  private static boolean isDisabledCondition(String[] anno, PsiElement psiElement) {
    ArrayList<PsiModifierListOwner> listOwners = new ArrayList<>();
    if (psiElement instanceof PsiMethod) {
      listOwners.add((PsiMethod)psiElement);
    }
    if (psiElement instanceof PsiClass) {
      listOwners.add((PsiClass)psiElement);
    }
    return ContainerUtil.exists(anno, an -> MetaAnnotationUtil.isMetaAnnotated(listOwners.get(0), Collections.singleton(an)));
  }

  public static String getDisabledCondition(PsiElement element) {
    if (isDisabledCondition(DISABLED_COND_ANNO, element)) {
      return "org.junit.*Disabled*Condition";
    }

    if (isDisabledCondition(ENABLED_COND_ANNO, element)) {
      return "org.junit.*Enabled*Condition";
    }

    if (isDisabledCondition(SCRIPT_COND_ANNO, element)) {
      return "org.junit.*DisabledIfCondition";
    }

    if (isDisabledCondition(DISABLED_ANNO, element)) {
      return "org.junit.*DisabledCondition";
    }
    return null;
  }
}

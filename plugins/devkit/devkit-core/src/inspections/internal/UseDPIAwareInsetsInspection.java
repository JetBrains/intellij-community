// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastCallKind;

import java.awt.*;

public class UseDPIAwareInsetsInspection extends AbstractUseDPIAwareBorderInspection {

  private static final String AWT_INSETS_CLASS_NAME = Insets.class.getName();
  private static final String JB_INSETS_CLASS_NAME = JBInsets.class.getName();
  private static final String JB_UI_CLASS_NAME = JBUI.class.getName();

  @Override
  protected boolean isAllowedConstructorCall(@NotNull UCallExpression expression) {
    // allow using 'new Insets()' in methods returning 'JBInsets'
    UElement parent = expression.getUastParent();
    if (!(parent instanceof UCallExpression containingCall) || containingCall.getKind() != UastCallKind.METHOD_CALL) return false;
    PsiType type = containingCall.getReturnType();
    if (!(type instanceof PsiClassType classType)) return false;
    PsiClass resolvedClass = classType.resolve();
    if (resolvedClass == null) return false;
    return JB_INSETS_CLASS_NAME.equals(resolvedClass.getQualifiedName());
  }

  @Override
  protected @NotNull String getFactoryMethodContainingClassName() {
    return JB_UI_CLASS_NAME;
  }

  @Override
  protected @NotNull String getFactoryMethodName() {
    return "insets";
  }

  @Override
  protected @NotNull String getNonDpiAwareClassName() {
    return AWT_INSETS_CLASS_NAME;
  }

  @Override
  protected @InspectionMessage @NotNull String getCanBeSimplifiedMessage() {
    return DevKitBundle.message("inspections.use.dpi.aware.insets.can.be.simplified");
  }

  @Override
  protected @NotNull LocalQuickFix createSimplifyFix() {
    return new SimplifyJBUIInsetsCreationQuickFix();
  }

  @Override
  protected @InspectionMessage @NotNull String getNonDpiAwareObjectCreatedMessage() {
    return DevKitBundle.message("inspections.use.dpi.aware.insets.not.dpi.aware");
  }

  @Override
  protected @NotNull LocalQuickFix createConvertToDpiAwareMethodCall() {
    return new ConvertToJBUIInsetsQuickFix();
  }


  private static abstract class AbstractConvertToDpiAwareInsetsQuickFix extends AbstractConvertToDpiAwareCallQuickFix {

    @Override
    protected @NotNull String getFactoryMethodContainingClassName() {
      return JB_UI_CLASS_NAME;
    }

    @Override
    protected @NotNull String getEmptyFactoryMethodName() {
      return "emptyInsets";
    }

    @Override
    protected @NotNull String getTopFactoryMethodName() {
      return "insetsTop";
    }

    @Override
    protected @NotNull String getBottomFactoryMethodName() {
      return "insetsBottom";
    }

    @Override
    protected @NotNull String getLeftFactoryMethodName() {
      return "insetsLeft";
    }

    @Override
    protected @NotNull String getRightFactoryMethodName() {
      return "insetsRight";
    }

    @Override
    protected @NotNull String getAllSameFactoryMethodName() {
      return "insets";
    }

    @Override
    protected @NotNull String getTopBottomAndLeftRightSameFactoryMethodName() {
      return "insets";
    }

    @Override
    protected @NotNull String getAllDifferentFactoryMethodName() {
      return "insets";
    }
  }

  private static class SimplifyJBUIInsetsCreationQuickFix extends AbstractConvertToDpiAwareInsetsQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.dpi.aware.insets.simplify.fix.name");
    }
  }

  private static class ConvertToJBUIInsetsQuickFix extends AbstractConvertToDpiAwareInsetsQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.dpi.aware.insets.convert.fix.name");
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UCallExpression;

import javax.swing.border.EmptyBorder;

public class UseDPIAwareEmptyBorderInspection extends AbstractUseDPIAwareBorderInspection {
  private static final String SWING_EMPTY_BORDER_CLASS_NAME = EmptyBorder.class.getName();
  private static final String JB_UI_CLASS_NAME = JBUI.class.getName();
  private static final String JB_UI_BORDERS_CLASS_NAME = JB_UI_CLASS_NAME + ".Borders";

  @Override
  protected boolean isAllowedConstructorCall(@NotNull UCallExpression expression) {
    return false;
  }

  @Override
  protected @NotNull String getFactoryMethodContainingClassName() {
    return JB_UI_BORDERS_CLASS_NAME;
  }

  @Override
  protected @NotNull String getFactoryMethodName() {
    return "empty";
  }

  @Override
  protected @NotNull String getNonDpiAwareClassName() {
    return SWING_EMPTY_BORDER_CLASS_NAME;
  }

  @Override
  protected @NotNull String getCanBeSimplifiedMessage() {
    return DevKitBundle.message("inspections.use.dpi.aware.empty.border.can.be.simplified");
  }

  @Override
  protected @NotNull LocalQuickFix createSimplifyFix() {
    return new SimplifyJBUIEmptyBorderCreationQuickFix();
  }

  @Override
  protected @NotNull String getNonDpiAwareObjectCreatedMessage() {
    return DevKitBundle.message("inspections.use.dpi.aware.empty.border.not.dpi.aware");
  }

  @Override
  protected @NotNull LocalQuickFix createConvertToDpiAwareMethodCall() {
    return new ConvertToJBUIBorderQuickFix();
  }

  private static abstract class AbstractConvertToDpiAwareBorderQuickFix extends AbstractConvertToDpiAwareCallQuickFix {

    @Override
    protected @NotNull String getFactoryMethodContainingClassName() {
      return JB_UI_BORDERS_CLASS_NAME;
    }

    @Override
    protected @NotNull String getEmptyFactoryMethodName() {
      return "empty";
    }

    @Override
    protected @NotNull String getTopFactoryMethodName() {
      return "emptyTop";
    }

    @Override
    protected @NotNull String getBottomFactoryMethodName() {
      return "emptyBottom";
    }

    @Override
    protected @NotNull String getLeftFactoryMethodName() {
      return "emptyLeft";
    }

    @Override
    protected @NotNull String getRightFactoryMethodName() {
      return "emptyRight";
    }

    @Override
    protected @NotNull String getAllSameFactoryMethodName() {
      return "empty";
    }

    @Override
    protected @NotNull String getTopBottomAndLeftRightSameFactoryMethodName() {
      return "empty";
    }

    @Override
    protected @NotNull String getAllDifferentFactoryMethodName() {
      return "empty";
    }
  }

  private static class SimplifyJBUIEmptyBorderCreationQuickFix extends AbstractConvertToDpiAwareBorderQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.dpi.aware.empty.border.simplify.fix.name");
    }
  }

  private static class ConvertToJBUIBorderQuickFix extends AbstractConvertToDpiAwareBorderQuickFix {
    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.dpi.aware.empty.border.convert.fix.name");
    }
  }
}

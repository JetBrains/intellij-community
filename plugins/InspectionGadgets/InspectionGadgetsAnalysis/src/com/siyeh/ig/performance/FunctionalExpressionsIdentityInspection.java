// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author okli
 */
public class FunctionalExpressionsIdentityInspection extends MapOrSetKeyInspection {

  @SuppressWarnings({"PublicField", "WeakerAccess", "CanBeFinal"}) 
  public boolean ignoreFuncInterfaceAnnotation = true;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("ignore.func.interface.annotation.option"),
                                          this, "ignoreFuncInterfaceAnnotation");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("functional.expression.identity");
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel8OrHigher(file);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final ClassType type = (ClassType)infos[0];
    return InspectionGadgetsBundle.message(
      "collection.contains.key.problem.descriptor", type);
  }

  @Override
  protected boolean shouldTriggerOnKeyType(PsiType argumentType) {
    PsiClass className = PsiUtil.resolveClassInClassTypeOnly(argumentType);
    if (className != null) {
      if (AnnotationUtil.isAnnotated(className, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, 0)
          && ignoreFuncInterfaceAnnotation) {
        return false;
      }
      return LambdaHighlightingUtil.checkInterfaceFunctional(className) == null
             && className.isInterface();
    }
    return false;
  }
}




// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.SetInspectionOptionFix;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class SortedCollectionWithNonComparableKeysInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<String> COLLECTIONS = Set.of(
    "java.util.TreeSet", "java.util.TreeMap", "java.util.concurrent.ConcurrentSkipListSet", "java.util.concurrent.ConcurrentSkipListMap");

  public boolean IGNORE_TYPE_PARAMETERS;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      JavaBundle.message("inspection.sorted.collection.with.non.comparable.keys.option.type.parameters"), this,
      "IGNORE_TYPE_PARAMETERS");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (expression.getAnonymousClass() != null || expression.isArrayCreation() ||
            expression.getArgumentList() == null || !expression.getArgumentList().isEmpty()) {
          return;
        }
        PsiJavaCodeReferenceElement reference = expression.getClassReference();
        if (reference == null) return;
        String qualifiedName = reference.getQualifiedName();
        if (!COLLECTIONS.contains(qualifiedName)) return;
        PsiClassType type = ObjectUtils.tryCast(expression.getType(), PsiClassType.class);
        if (type == null || type.isRaw()) return;
        PsiType elementType = ArrayUtil.getFirstElement(type.getParameters());
        if (elementType == null || TypeUtils.isJavaLangObject(elementType)) return;
        LocalQuickFix fix = null;
        if (elementType instanceof PsiClassType && ((PsiClassType)elementType).resolve() instanceof PsiTypeParameter) {
          if (IGNORE_TYPE_PARAMETERS) return;
          String message = JavaBundle.message("inspection.sorted.collection.with.non.comparable.keys.option.type.parameters");
          fix = new SetInspectionOptionFix(SortedCollectionWithNonComparableKeysInspection.this, "IGNORE_TYPE_PARAMETERS", message, true);
        }
        if (InheritanceUtil.isInheritor(elementType, CommonClassNames.JAVA_LANG_COMPARABLE)) return;
        holder.registerProblem(expression, JavaBundle.message("inspection.sorted.collection.with.non.comparable.keys.message"), fix);
      }
    };
  }
}

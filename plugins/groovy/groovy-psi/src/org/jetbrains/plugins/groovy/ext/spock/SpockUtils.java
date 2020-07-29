// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Map;

import static org.jetbrains.plugins.groovy.ext.spock.DataVariablesKt.createVariableMap;

/**
 * @author Sergey Evdokimov
 */
public final class SpockUtils {

  public static final String SPEC_CLASS_NAME = "spock.lang.Specification";

  private SpockUtils() {}

  public static Map<String, SpockVariableDescriptor> getVariableMap(@NotNull GrMethod method) {
    GrMethod originalMethod;

    PsiFile containingFile = method.getContainingFile();
    if (containingFile != containingFile.getOriginalFile()) {
      int methodOffset = method.getTextOffset();
      PsiElement originalPlace = containingFile.getOriginalFile().findElementAt(methodOffset);
      originalMethod = PsiTreeUtil.getParentOfType(originalPlace, GrMethod.class);
      assert originalMethod != null : containingFile.getOriginalFile().getText().substring(Math.max(0, methodOffset - 50), Math.min(methodOffset + 50, containingFile.getOriginalFile().getText().length()));
    }
    else {
      originalMethod = method;
    }

    return CachedValuesManager.getCachedValue(originalMethod, () -> Result.create(createVariableMap(originalMethod), originalMethod));
  }

  @Nullable
  public static String getNameByReference(@Nullable PsiElement expression) {
    if (!(expression instanceof GrReferenceExpression)) return null;

    PsiElement firstChild = expression.getFirstChild();
    if (firstChild != expression.getLastChild() || !PsiImplUtil.isLeafElementOfType(firstChild, GroovyTokenTypes.mIDENT)) return null;

    GrReferenceExpression ref = (GrReferenceExpression)expression;
    if (ref.isQualified()) return null;

    return ref.getReferenceName();
  }

  public static boolean isTestMethod(PsiElement element) {
    if (!(element instanceof GrMethod)) return false;
    GrMethod method = ((GrMethod)element);
    PsiClass clazz = method.getContainingClass();
    if (!isSpecification(clazz)) return false;
    if (isFixtureMethod(method)) return false;
    return isFeatureMethod(method);
  }

  public static boolean isSpecification(@Nullable PsiClass clazz) {
    return clazz instanceof GrTypeDefinition && InheritanceUtil.isInheritor(clazz, SPEC_CLASS_NAME);
  }

  public static boolean isFixtureMethod(@NotNull GrMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    return SpockConstants.FIXTURE_METHOD_NAMES.contains(method.getName());
  }

  public static boolean isFeatureMethod(@NotNull GrMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;

    GrOpenBlock block = method.getBlock();
    if (block == null) return false;

    for (GrStatement statement : block.getStatements()) {
      if (!(statement instanceof GrLabeledStatement)) {
        continue;
      }
      String label = ((GrLabeledStatement)statement).getName();
      if (SpockConstants.FEATURE_METHOD_LABELS.contains(label)) return true;
    }
    return false;
  }
}

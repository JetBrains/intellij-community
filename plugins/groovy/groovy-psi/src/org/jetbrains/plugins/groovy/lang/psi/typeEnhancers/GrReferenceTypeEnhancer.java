// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public abstract class GrReferenceTypeEnhancer {

  public static final ExtensionPointName<GrReferenceTypeEnhancer> EP_NAME = ExtensionPointName.create("org.intellij.groovy.referenceTypeEnhancer");

  public abstract @Nullable PsiType getReferenceType(GrReferenceExpression ref, @Nullable PsiElement resolved);

}

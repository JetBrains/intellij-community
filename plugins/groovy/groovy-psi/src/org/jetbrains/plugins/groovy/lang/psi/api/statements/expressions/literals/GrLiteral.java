// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals;

import com.intellij.model.psi.PsiExternalReferenceHost;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public interface GrLiteral extends GrExpression, GrLiteralContainer, PsiExternalReferenceHost {
  boolean isString();
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals;

import com.intellij.model.psi.PsiExternalReferenceHost;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public interface GrLiteral extends GrExpression, GrLiteralContainer, PsiExternalReferenceHost {
}

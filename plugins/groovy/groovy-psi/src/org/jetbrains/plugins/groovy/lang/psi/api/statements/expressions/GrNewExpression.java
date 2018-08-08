// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

public interface GrNewExpression extends GrCallExpression, GrConstructorCall {

  @Nullable
  GrCodeReferenceElement getReferenceElement();

  @Nullable
  GrTypeElement getTypeElement();

  int getArrayCount();

  @Nullable
  GrAnonymousClassDefinition getAnonymousClassDefinition();

  @Nullable
  GrArrayDeclaration getArrayDeclaration();

  @Nullable
  GrArrayInitializer getArrayInitializer();

  @Nullable
  GrTypeArgumentList getConstructorTypeArguments();
}

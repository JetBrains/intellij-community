/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public interface GrSuperReferenceExpression extends GrExpression {
  @Nullable
  GrReferenceExpression getQualifier();
}
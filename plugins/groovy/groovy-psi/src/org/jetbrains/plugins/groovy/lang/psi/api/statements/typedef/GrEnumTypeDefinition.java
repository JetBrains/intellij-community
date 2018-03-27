/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;

/**
 * @author Dmitry.Krasilschikov
 */
public interface GrEnumTypeDefinition extends GrTypeDefinition {

  @NotNull
  GrEnumConstant[] getEnumConstants();

  @Nullable
  GrEnumConstantList getEnumConstantList();
}

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 20, 2007
 */
public interface AntNameIdentifier extends AntElement {
  String getIdentifierName();
  
  void setIdentifierName(@NotNull String name) throws IncorrectOperationException;
}

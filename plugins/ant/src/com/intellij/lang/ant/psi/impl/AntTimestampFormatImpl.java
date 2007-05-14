/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlTag;

/**
 * @author Eugene Zhuravlev
 *         Date: May 14, 2007
 */
public class AntTimestampFormatImpl extends AntStructuredElementImpl{
  public AntTimestampFormatImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition, AntFileImpl.PROPERTY);
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      final AntElement parent = getAntParent();
      if (parent != null) {
        parent.clearCaches();
      }
    }
  }
}

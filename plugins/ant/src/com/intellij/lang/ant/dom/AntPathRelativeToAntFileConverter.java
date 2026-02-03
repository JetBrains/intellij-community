// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class AntPathRelativeToAntFileConverter extends AntPathConverter {
  @Override
  protected AntDomProject getEffectiveAntProject(GenericAttributeValue attribValue) {
    return attribValue.getParentOfType(AntDomProject.class, false);
  }

  @Override
  protected @Nullable @NlsSafe String getPathResolveRoot(ConvertContext context, AntDomProject antProject) {
    return antProject.getContainingFileDir();
  }
}

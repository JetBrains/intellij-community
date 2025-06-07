// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.dom.AntDomFileDescription;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class AntHectorPanelProvider implements HectorComponentPanelsProvider{
  @Override
  public HectorComponentPanel createConfigurable(final @NotNull PsiFile file) {
    if (file instanceof XmlFile && AntDomFileDescription.isAntFile(((XmlFile)file))) {
      return new AntHectorConfigurable(((XmlFile)file));
    }
    return null;
  }
}

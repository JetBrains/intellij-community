/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author peter
 */
public class DevKitIconProvider extends IconProvider {
  private static final Icon ICON = IconLoader.getIcon("/plugin.png");
  
  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    @NonNls final String pluginXml = "plugin.xml";
    if (element instanceof XmlFile && Comparing.strEqual(((XmlFile)element).getName(), pluginXml)) {
      return ICON;
    }
    return null;
  }

}

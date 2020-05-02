// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.paths.PathReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Dependency;

public abstract class DependencyImpl implements Dependency {

  @Override
  public @Nullable XmlFile getResolvedConfigFile() {
    final GenericAttributeValue<PathReference> configFileAttribute = getConfigFile();
    if (!DomUtil.hasXml(configFileAttribute)) return null;

    final PathReference configFile = configFileAttribute.getValue();
    if (configFile == null) return null;

    final PsiElement resolve = configFile.resolve();
    if (!(resolve instanceof XmlFile)) return null;
    return (XmlFile)resolve;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;

/**
 * @author Dmitry Avdeev
 */
public abstract class ExtensionImpl implements Extension {

  @Override
  public @Nullable ExtensionPoint getExtensionPoint() {
    final DomElement domDeclaration = getChildDescription().getDomDeclaration();
    if (domDeclaration instanceof ExtensionPoint) {
      return (ExtensionPoint)domDeclaration;
    }
    return null;
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.Stubbed;
import com.intellij.util.xml.SubTagList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ExtensionPoints extends DomElement {

  @Stubbed
  @SubTagList("extensionPoint")
  @NotNull List<? extends ExtensionPoint> getExtensionPoints();

  ExtensionPoint addExtensionPoint();
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

@Presentation(icon = "AllIcons.Nodes.Module")
public interface PluginModule extends DomElement {

  @Stubbed
  @NameValue
  @Required
  @NotNull GenericAttributeValue<String> getValue();
}

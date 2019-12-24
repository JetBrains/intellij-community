// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

@Presentation(icon = "AllIcons.Nodes.Module")
public interface PluginModule extends DomElement {

  @NotNull
  @Stubbed
  @NameValue
  @Required
  GenericAttributeValue<String> getValue();
}

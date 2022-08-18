// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Presentation(icon = "AllIcons.Nodes.Module")
public interface ModuleComponents extends DomElement {

  @NotNull List<? extends Component.Module> getComponents();

  Component.Module addComponent();
}

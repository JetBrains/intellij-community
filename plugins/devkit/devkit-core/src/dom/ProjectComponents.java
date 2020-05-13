// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Presentation(icon = "AllIcons.Nodes.Project")
public interface ProjectComponents extends DomElement {

  @NotNull
  List<Component.Project> getComponents();

  Component.Project addComponent();
}

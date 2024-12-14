// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupPresentationProvider;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;

@Presentation(provider = ActionOrGroupPresentationProvider.ForAddToGroup.class)
public interface AddToGroup extends DomElement {

  @NotNull
  GenericAttributeValue<Anchor> getAnchor();


  @NotNull
  @Convert(ActionOrGroupResolveConverter.class)
  GenericAttributeValue<ActionOrGroup> getRelativeToAction();


  @NotNull
  @Required
  @Convert(ActionOrGroupResolveConverter.OnlyGroups.class)
  GenericAttributeValue<ActionOrGroup> getGroupId();

  enum Anchor {
    first, last, before, after
  }
}

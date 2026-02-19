// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Referencing;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupPresentationProvider;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupReferencingConverter;

@Presentation(provider = ActionOrGroupPresentationProvider.ForAddToGroup.class)
public interface AddToGroup extends DomElement {

  @NotNull
  GenericAttributeValue<Anchor> getAnchor();


  @NotNull
  @Referencing(ActionOrGroupReferencingConverter.class)
  GenericAttributeValue<String> getRelativeToAction();


  @NotNull
  @Required
  @Referencing(ActionOrGroupReferencingConverter.OnlyGroups.class)
  GenericAttributeValue<String> getGroupId();

  enum Anchor {
    first, last, before, after
  }
}

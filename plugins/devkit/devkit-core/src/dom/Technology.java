// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.NameValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * {@code <technology id="..." name="..." description="..." icon="..."/>}: a technology that the plugin or the content
 * module declares. The key {@code technology.<id>.name} or {@code technology.<id>.description} of the
 * {@code <resource-bundle>} of the module wins over the attribute.
 */
public interface Technology extends DomElement {
  @NameValue
  @Required
  @NotNull GenericAttributeValue<String> getId();

  @Required(false)
  @NotNull GenericAttributeValue<String> getName();

  @Required(false)
  @NotNull GenericAttributeValue<String> getDescription();

  @Required(false)
  @NotNull GenericAttributeValue<String> getIcon();
}

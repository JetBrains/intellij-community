// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Presentation(typeName = "Extension")
public interface Extension extends DomElement {

  String ID_ATTRIBUTE = "id";
  String ORDER_ATTRIBUTE = "order";
  String OS_ATTRIBUTE = "os";

  String IMPLEMENTATION_ATTRIBUTE = "implementation";

  @NotNull
  @Override
  XmlTag getXmlTag();

  @NameValue
  @Required(value = false)
  @Attribute(ID_ATTRIBUTE)
  GenericAttributeValue<String> getId();

  @Referencing(value = ExtensionOrderConverter.class, soft = true)
  @Required(value = false)
  @Attribute(ORDER_ATTRIBUTE)
  GenericAttributeValue<String> getOrder();

  @NotNull
  @Attribute(OS_ATTRIBUTE)
  GenericAttributeValue<IdeaPluginDescriptorImpl.OS> getOs();

  @Nullable
  ExtensionPoint getExtensionPoint();

  static boolean isClassField(@NotNull String fieldName) {
    return fieldName.equals(IMPLEMENTATION_ATTRIBUTE) ||
           fieldName.equals("className") ||
           fieldName.equals("serviceInterface") ||
           fieldName.equals("serviceImplementation") ||
           (fieldName.endsWith("Class") && !fieldName.equals("forClass"));
  }
}

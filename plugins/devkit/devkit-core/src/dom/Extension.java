// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.extensions.ExtensionDescriptor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.NameValue;
import com.intellij.util.xml.Referencing;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.impl.ExtensionOrderConverter;

@Presentation(typeName = DevkitDomPresentationConstants.EXTENSION)
public interface Extension extends DomElement {

  @NonNls String ID_ATTRIBUTE = "id";
  @NonNls String ORDER_ATTRIBUTE = "order";
  @NonNls String OS_ATTRIBUTE = "os";

  @NonNls String IMPLEMENTATION_ATTRIBUTE = "implementation";

  @NotNull
  @Override
  XmlTag getXmlTag();

  @NameValue
  @Required(false)
  @Attribute(ID_ATTRIBUTE)
  GenericAttributeValue<String> getId();

  @Referencing(value = ExtensionOrderConverter.class, soft = true)
  @Required(false)
  @Attribute(ORDER_ATTRIBUTE)
  GenericAttributeValue<String> getOrder();

  @NotNull
  @Attribute(OS_ATTRIBUTE)
  GenericAttributeValue<ExtensionDescriptor.Os> getOs();

  /**
   * @return extension declaration or {@code null} if unresolved
   */
  @Nullable
  ExtensionPoint getExtensionPoint();

  static boolean isClassField(@NotNull @NonNls String fieldName) {
    return fieldName.equals(IMPLEMENTATION_ATTRIBUTE) ||
           fieldName.equals("className") ||
           fieldName.equals("serviceInterface") ||
           fieldName.equals("serviceImplementation") ||
           fieldName.equals("class") ||
           fieldName.endsWith("ClassName") ||
           (fieldName.endsWith("Class") && !fieldName.equals("forClass"));
  }

  /**
   * @return whether the field is any known class name field.
   */
  static boolean isClassNameField(@NotNull @NonNls String fieldName) {
    return isClassField(fieldName) || fieldName.equals("forClass");
  }
}

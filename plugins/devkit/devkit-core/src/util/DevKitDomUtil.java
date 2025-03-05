// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public final class DevKitDomUtil {

  public static @Nullable GenericAttributeValue<?> getAttribute(DomElement domElement, @NonNls String attributeName) {
    final DomAttributeChildDescription<?> attributeDescription = domElement.getGenericInfo().getAttributeChildDescription(attributeName);
    if (attributeDescription == null) {
      return null;
    }

    return attributeDescription.getDomAttributeValue(domElement);
  }

  public static @Nullable GenericDomValue<?> getTag(DomElement domElement, @NonNls String tagName) {
    final DomFixedChildDescription fixedChildDescription = domElement.getGenericInfo().getFixedChildDescription(tagName);
    if (fixedChildDescription == null) {
      return null;
    }
    final DomElement domValueElement = ContainerUtil.getFirstItem(fixedChildDescription.getValues(domElement));
    return ObjectUtils.tryCast(domValueElement, GenericDomValue.class);
  }
}

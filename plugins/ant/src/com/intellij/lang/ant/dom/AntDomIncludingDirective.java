// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomIncludingDirective extends AntDomElement {
  private static final @NlsSafe String DEFAULT_SEPARATOR = ".";

  @Attribute("file")
  @Convert(value = AntPathRelativeToAntFileConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Attribute("optional")
  @Convert(value = AntBooleanConverterDefaultFalse.class)
  public abstract GenericAttributeValue<Boolean> isOptional();

  @Attribute("as")
  public abstract GenericAttributeValue<String> getTargetPrefix();

  @Attribute("prefixSeparator")
  public abstract GenericAttributeValue<String> getTargetPrefixSeparator();

  public final @NotNull @NlsSafe String getTargetPrefixSeparatorValue() {
    final GenericAttributeValue<String> separator = getTargetPrefixSeparator();
    if (separator == null) {
      return DEFAULT_SEPARATOR;
    }
    final String separatorValue = separator.getStringValue();
    return separatorValue != null? separatorValue : DEFAULT_SEPARATOR;
  }
}

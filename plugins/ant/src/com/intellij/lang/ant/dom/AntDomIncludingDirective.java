/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.dom;

import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomIncludingDirective extends AntDomElement {
  private static final String DEFAULT_SEPARATOR = ".";

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

  @NotNull
  public final String getTargetPrefixSeparatorValue() {
    final GenericAttributeValue<String> separator = getTargetPrefixSeparator();
    if (separator == null) {
      return DEFAULT_SEPARATOR;
    }
    final String separatorValue = separator.getStringValue();
    return separatorValue != null? separatorValue : DEFAULT_SEPARATOR;
  }
}

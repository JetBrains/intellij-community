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

package org.jetbrains.android.dom.converters;

import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.NamedEnumUtil;
import com.intellij.util.xml.converters.DelimitedListConverter;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: Eugene.Kudelevsky
* Date: Mar 30, 2009
* Time: 6:54:02 PM
* To change this template use File | Settings | File Templates.
*/
public class FormatConverter extends DelimitedListConverter<AttributeFormat> {
  public FormatConverter() {
    super("|");
  }

  protected AttributeFormat convertString(@Nullable String string, ConvertContext context) {
    if (string == null) return null;
    return NamedEnumUtil.getEnumElementByValue(AttributeFormat.class, StringUtil.capitalize(string));
  }

  protected String toString(@Nullable AttributeFormat format) {
    if (format == null) return null;
    return format.name().toLowerCase();
  }

  protected Object[] getReferenceVariants(final ConvertContext context, final GenericDomValue<List<AttributeFormat>> value) {
    List<AttributeFormat> variants = new ArrayList<AttributeFormat>();
    Collections.addAll(variants, AttributeFormat.values());
    filterVariants(variants, value);
    String[] stringVariants = new String[variants.size()];
    for (int i = 0, variantsSize = variants.size(); i < variantsSize; i++) {
      stringVariants[i] = StringUtil.decapitalize(variants.get(i).name());
    }
    return stringVariants;
  }

  protected PsiElement resolveReference(@Nullable final AttributeFormat s, final ConvertContext context) {
    return s == null ? null : context.getReferenceXmlElement();
  }

  protected String getUnresolvedMessage(final String value) {
    return MessageFormat.format(AndroidBundle.message("cannot.resolve.format.error"), value);
  }
}

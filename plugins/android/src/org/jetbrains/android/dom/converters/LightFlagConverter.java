/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.dom.converters;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Apr 1, 2009
 * Time: 7:45:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class LightFlagConverter extends ResolvingConverter<String> {
  private final Set<String> myValues = new HashSet<String>();

  public LightFlagConverter(String... values) {
    Collections.addAll(myValues, values);
  }

  @NotNull
  public Collection<? extends String> getVariants(ConvertContext context) {
    Set<String> result = new HashSet<String>();
    XmlElement element = context.getXmlElement();
    if (element == null) return result;
    String attrValue = ((XmlAttribute)element).getValue();
    String[] flags = attrValue.split("\\|");
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < flags.length - 1; i++) {
      String flag = flags[i];
      if (!myValues.contains(flag)) break;
      prefix.append(flag).append('|');
    }
    for (String value : myValues) {
      result.add(prefix.toString() + value);
    }
    return result;
  }

  public String fromString(@Nullable String s, ConvertContext convertContext) {
    return s;
  }

  public String toString(@Nullable String s, ConvertContext convertContext) {
    return s;
  }
}

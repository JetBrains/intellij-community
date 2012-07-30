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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 12, 2009
 * Time: 2:50:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class StyleItemNameConverter extends ResolvingConverter<String> {
  @NotNull
  @Override
  public Collection<? extends String> getVariants(ConvertContext context) {
    List<String> result = new ArrayList<String>();
    ResourceManager manager = SystemResourceManager.getInstance(context);
    if (manager != null) {
      AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
      if (attrDefs != null) {
        for (String name : attrDefs.getAttributeNames()) {
          result.add("android:" + name);
        }
      }
    }
    return result;
  }

  @Override
  public LookupElement createLookupElement(String s) {
    final String prefix = "android:";
    if (s == null || !s.startsWith(prefix)) {
      return null;
    }
    final String attributeName = s.substring(prefix.length());
    return LookupElementBuilder.create(s).withLookupString(attributeName);
  }

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    String[] strs = s.split(":");
    if (strs.length < 2 || !"android".equals(strs[0])) {
      return s;
    }
    if (strs.length == 2) {
      ResourceManager manager = SystemResourceManager.getInstance(context);
      if (manager != null) {
        AttributeDefinitions attrDefs = manager.getAttributeDefinitions();
        if (attrDefs != null && attrDefs.getAttrDefByName(strs[1]) != null) {
          return s;
        }
      }
    }
    return null;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}

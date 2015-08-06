/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class StorageDataBase {
  protected static final Logger LOG = Logger.getInstance(StorageDataBase.class);

  public static final String COMPONENT = "component";
  public static final String NAME = "name";

  @NotNull
  public abstract Set<String> getComponentNames();

  public abstract boolean hasState(@NotNull String componentName);

  @Nullable
  static String getComponentNameIfValid(@NotNull Element element) {
    String name = element.getAttributeValue(NAME);
    if (StringUtil.isEmpty(name)) {
      LOG.warn("No name attribute for component in " + JDOMUtil.writeElement(element));
      return null;
    }
    return name;
  }
}
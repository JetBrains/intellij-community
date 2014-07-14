/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

public abstract class ExtensionPointImpl implements ExtensionPoint {

  @NotNull
  @Override
  public String getEffectiveName() {
    if (DomUtil.hasXml(getName())) {
      return getName().getRawText();
    }
    return getQualifiedName().getRawText();
  }

  @Nullable
  @Override
  public String getNamePrefix() {
    if (DomUtil.hasXml(getQualifiedName())) {
      return null;
    }

    final IdeaPlugin plugin = getParentOfType(IdeaPlugin.class, false);
    if (plugin == null) return null;

    return StringUtil.notNullize(plugin.getPluginId(), "com.intellij");
  }

  @NotNull
  @Override
  public String getEffectiveQualifiedName() {
    if (DomUtil.hasXml(getQualifiedName())) {
      return getQualifiedName().getRawText();
    }

    return getNamePrefix() + "." + getName().getRawText();
  }
}

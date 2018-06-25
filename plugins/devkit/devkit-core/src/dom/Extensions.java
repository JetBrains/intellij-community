/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ExtensionNsConverter;

import java.util.List;

public interface Extensions extends DomElement {

  @NonNls
  String DEFAULT_PREFIX = "com.intellij";

  @NotNull
  @Attribute("defaultExtensionNs")
  @Convert(value = ExtensionNsConverter.class, soft = true)
  @Stubbed
  GenericAttributeValue<IdeaPlugin> getDefaultExtensionNs();

  /**
   * @deprecated use {@link #getDefaultExtensionNs()}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @NotNull
  @Convert(value = ExtensionNsConverter.class, soft = true)
  @Stubbed
  @Deprecated
  GenericAttributeValue<IdeaPlugin> getXmlns();

  List<Extension> getExtensions();

  Extension addExtension();

  Extension addExtension(String qualifiedEPName);

  @NotNull
  String getEpPrefix();
}

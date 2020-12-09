// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ExtensionNsConverter;

import java.util.List;

public interface Extensions extends DomElement {

  @NotNull
  @Override
  XmlTag getXmlTag();

  @NonNls
  String DEFAULT_PREFIX = PluginManagerCore.CORE_PLUGIN_ID;

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

  /**
   * Returns all present extensions.
   */
  List<Extension> collectExtensions();

  /**
   * @deprecated dummy method for DOM, use {@link #collectExtensions()}.
   */
  @Deprecated
  List<Extension> getExtensions();

  Extension addExtension();

  Extension addExtension(String qualifiedEPName);

  @NotNull @NlsSafe
  String getEpPrefix();

  
  /**
   * Special marker for extension that cannot be resolved using current dependencies.
   */
  interface UnresolvedExtension extends DomElement {
  }
}

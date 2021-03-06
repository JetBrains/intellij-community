// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.settings;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;

public class ConfigurableExtensionTest extends LightPlatformTestCase {
  public void testDeserialize() throws Exception {
    final Element element = JDOMUtil.load(
      "<projectConfigurable instance=\"com.intellij.javaee.ExternalResourceConfigurable\" key=\"display.name.edit.external.resource\" bundle=\"messages.XmlBundle\">\n" +
      "    <configurable instance=\"com.intellij.javaee.XMLCatalogConfigurable\" displayName=\"XML Catalog\"/>\n" +
      "  </projectConfigurable>");

    ConfigurableEP<?> bean = new ConfigurableEP<>(new DefaultPluginDescriptor("ConfigurableExtensionTest"));
    XmlSerializer.deserializeInto(element, bean);
    assertNotNull(bean.children);
    assertEquals(1, bean.children.size());
  }
}

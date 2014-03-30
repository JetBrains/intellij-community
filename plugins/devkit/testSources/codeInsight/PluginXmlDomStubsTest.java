/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.util.xml.stubs.DomStubTest;

public class PluginXmlDomStubsTest extends DomStubTest {

  public void testStubs() {
    doBuilderTest("pluginXmlStubs.xml",
                  "File:idea-plugin\n" +
                  "  Element:idea-plugin\n" +
                  "    Element:id\n" +
                  "    Element:name\n" +
                  "    Element:depends\n" +
                  "    Element:module\n" +
                  "      Attribute:value:myModule\n" +
                  "    Element:extensions\n" +
                  "      Attribute:xmlns:someNS\n" +
                  "      Attribute:defaultExtensionNs:com.intellij\n" +
                  "    Element:extensionPoints\n" +
                  "      Element:extensionPoint\n" +
                  "        Attribute:name:myEP\n" +
                  "        Attribute:interface:SomeInterface\n" +
                  "        Element:with\n" +
                  "          Attribute:attribute:attributeName\n" +
                  "          Attribute:implements:SomeImplements\n" +
                  "      Element:extensionPoint\n" +
                  "        Attribute:qualifiedName:qualifiedName\n" +
                  "        Attribute:beanClass:BeanClass\n"
    );
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/codeInsight";
  }
}

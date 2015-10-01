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
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.xml.stubs.DomStubTest;

@TestDataPath("$CONTENT_ROOT/testData/pluginXmlDomStubs")
public class PluginXmlDomStubsTest extends DomStubTest {

  public void testStubs() {
    doBuilderTest("pluginXmlStubs.xml",
                  "File:idea-plugin\n" +
                  "  Element:idea-plugin\n" +
                  "    Element:id:com.intellij.myPlugin\n" +
                  "    Element:name:pluginName\n" +
                  "    Element:depends:anotherPlugin\n" +
                  "    Element:module\n" +
                  "      Attribute:value:myModule\n" +
                  "    Element:extensionPoints\n" +
                  "      Element:extensionPoint\n" +
                  "        Attribute:name:myEP\n" +
                  "        Attribute:interface:SomeInterface\n" +
                  "        Element:with\n" +
                  "          Attribute:attribute:attributeName\n" +
                  "          Attribute:implements:SomeImplements\n" +
                  "      Element:extensionPoint\n" +
                  "        Attribute:qualifiedName:qualifiedName\n" +
                  "        Attribute:beanClass:BeanClass\n" +
                  "    Element:extensions\n" +
                  "      Attribute:defaultExtensionNs:defaultExtensionNs\n" +
                  "      Attribute:xmlns:extensionXmlNs\n" +
                  "    Element:actions\n" +
                  "      Element:action\n" +
                  "        Attribute:id:actionId\n" +
                  "        Attribute:text:actionText\n" +
                  "      Element:group\n" +
                  "        Attribute:id:groupId\n" +
                  "        Element:action\n" +
                  "          Attribute:id:groupAction\n" +
                  "          Attribute:text:groupActionText\n" +
                  "        Element:group\n" +
                  "          Attribute:id:nestedGroup\n" +
                  "          Element:action\n" +
                  "            Attribute:id:nestedGroupActionId\n" +
                  "            Attribute:text:nestedGroupActionText\n");
  }

  public void testXInclude() throws Exception {
    prepareFile("pluginWithXInclude-extensionPoints.xml");
    prepareFile("pluginWithXInclude-main.xml");
    prepareFile("pluginWithXInclude.xml");
    myFixture.testHighlighting("pluginWithXInclude.xml");
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/pluginXmlDomStubs";
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.stubs.DomStubTest;
import com.intellij.xml.util.IncludedXmlTag;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.Actions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/pluginXmlDomStubs")
public class PluginXmlDomStubsTest extends DomStubTest {

  public void testStubs() {
    doBuilderTest("pluginXmlStubs.xml",
                  "File:idea-plugin\n" +
                  "  Element:idea-plugin\n" +
                  "    Element:id:com.intellij.myPlugin\n" +
                  "    Element:name:pluginName\n" +
                  "    Element:depends:anotherPlugin\n" +
                  "      Attribute:config-file:anotherPlugin.xml\n" +
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
                  "        Attribute:description:descriptionText\n" +
                  "        Attribute:popup:false\n" +
                  "      Element:group\n" +
                  "        Attribute:id:groupId\n" +
                  "        Attribute:description:groupDescriptionText\n" +
                  "        Attribute:text:groupText\n" +
                  "        Attribute:popup:true\n" +
                  "        Element:action\n" +
                  "          Attribute:id:groupAction\n" +
                  "          Attribute:text:groupActionText\n" +
                  "          Attribute:description:groupActionDescriptionText\n" +
                  "        Element:group\n" +
                  "          Attribute:id:nestedGroup\n" +
                  "          Element:action\n" +
                  "            Attribute:id:nestedGroupActionId\n" +
                  "            Attribute:text:nestedGroupActionText\n");
  }

  public void testXInclude() {
    prepareFile("pluginWithXInclude-extensionPoints.xml");
    prepareFile("pluginWithXInclude-main.xml");
    prepareFile("pluginWithXInclude.xml");
    myFixture.testHighlighting("pluginWithXInclude.xml");
  }

  public void testIncludedActions() {
    prepareFile("XIncludeWithActions.xml");
    DomFileElement<IdeaPlugin> element = prepare("XIncludeWithActions-main.xml", IdeaPlugin.class);

    XmlTag[] tags = element.getRootTag().getSubTags();
    assertEquals(2, tags.length);
    XmlTag included = tags[0];
    assertTrue(included instanceof IncludedXmlTag);
    assertEquals("actions", included.getName());

    List<Actions> actions = element.getRootElement().getActions();
    assertEquals(1, actions.size());
    assertNotNull(actions.get(0).getXmlTag());

    Action action = actions.get(0).getGroups().get(0).getActions().get(0);
    DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(action.getId());
    assertNotNull(handler.getStub());

    assertNotNull(DomTarget.getTarget(action));
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "pluginXmlDomStubs";
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.stubs.DomStubTest;
import com.intellij.util.xml.stubs.index.DomElementClassIndex;
import com.intellij.xml.util.IncludedXmlTag;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.Actions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.ProductDescriptor;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@TestDataPath("$CONTENT_ROOT/testData/pluginXmlDomStubs")
public class PluginXmlDomStubsTest extends DomStubTest {

  public void testStubs() {
    doBuilderTest("pluginXmlStubs.xml",
                  """
                    File:idea-plugin
                      Element:idea-plugin
                        Attribute:package:idea.plugin.package
                        Element:id:com.intellij.myPlugin
                        Element:name:pluginName
                        Element:depends:anotherPlugin
                          Attribute:config-file:anotherPlugin.xml
                          Attribute:optional:true
                        Element:module
                          Attribute:value:myModule
                        Element:content
                          Element:module
                            Attribute:name:module.name
                        Element:dependencies
                          Element:module
                            Attribute:name:dependencies.module
                          Element:plugin
                            Attribute:id:dependencies.plugin.id
                        Element:resource-bundle:MyResourceBundle
                        Element:idea-version
                          Attribute:since-build:sinceBuildValue
                          Attribute:until-build:untilBuildValue
                        Element:extensionPoints
                          Element:extensionPoint
                            Attribute:name:myEP
                            Attribute:interface:SomeInterface
                            Attribute:dynamic:true
                            Element:with
                              Attribute:attribute:attributeName
                              Attribute:implements:SomeImplements
                          Element:extensionPoint
                            Attribute:qualifiedName:qualifiedName
                            Attribute:beanClass:BeanClass
                        Element:extensions
                          Attribute:defaultExtensionNs:com.intellij
                        Element:extensions
                          Attribute:defaultExtensionNs:defaultExtensionNs
                          Attribute:xmlns:extensionXmlNs
                        Element:actions
                          Attribute:resource-bundle:ActionsResourceBundle
                          Element:action
                            Attribute:id:actionId
                            Attribute:text:actionText
                            Attribute:description:descriptionText
                            Attribute:popup:false
                          Element:group
                            Attribute:id:groupId
                            Attribute:description:groupDescriptionText
                            Attribute:text:groupText
                            Attribute:popup:true
                            Element:action
                              Attribute:id:groupAction
                              Attribute:text:groupActionText
                              Attribute:description:groupActionDescriptionText
                            Element:group
                              Attribute:id:nestedGroup
                              Element:action
                                Attribute:id:nestedGroupActionId
                                Attribute:text:nestedGroupActionText
                    """);
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

    List<? extends Actions> actions = element.getRootElement().getActions();
    assertEquals(2, actions.size());

    assertNotNull(actions.get(1).getXmlTag());
    Action action = actions.get(1).getGroups().get(0).getActions().get(0);
    DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(action.getId());
    assertNotNull(handler.getStub());

    assertNotNull(DomTarget.getTarget(action));
  }

  public void testStubIndexingThreadDoesNotLeaveExtensionsEmptyForEveryone() throws Exception {
    XmlFile file = prepareFile("pluginXmlStubs.xml");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    DomManager manager = DomManager.getDomManager(getProject());

    for (int i = 0; i < 10; i++) {
      myFixture.type(' ');
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
        for (XmlTag tag : SyntaxTraverser.psiTraverser(file).filter(XmlTag.class)) {
          assertNotNull(tag.getText(), manager.getDomElement(tag));
        }
      }));

      // index the file
      DomFileElement<IdeaPlugin> ideaPlugin = manager.getFileElement(file, IdeaPlugin.class);
      assertFalse(DomElementClassIndex.getInstance().hasStubElementsOfType(ideaPlugin, ProductDescriptor.class));

      future.get(20, TimeUnit.SECONDS);
    }
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "pluginXmlDomStubs";
  }
}

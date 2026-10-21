// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.stubs.DomStubTestUtil;
import com.intellij.util.xml.stubs.index.DomElementClassIndex;
import com.intellij.xml.util.IncludedXmlTag;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.Actions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.ProductDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/devkit/devkit-java-tests/testData/pluginXmlDomStubs")
public class PluginXmlDomStubsTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testStubs() {
    doBuilderTest("pluginXmlStubs.xml",
                  """
                    File:idea-plugin
                      Element:idea-plugin
                        Attribute:package:idea.plugin.package
                        Attribute:implementation-detail:true
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
                          Element:module
                            Attribute:name:optional.module
                            Attribute:loading:optional
                          Element:module
                            Attribute:name:required.module
                            Attribute:loading:required
                          Element:module
                            Attribute:name:ondemand.module
                            Attribute:loading:on-demand
                          Element:module
                            Attribute:name:required.in.frontend
                            Attribute:required-if-available:intellij.platform.frontend
                        Element:dependencies
                          Element:module
                            Attribute:name:dependencies.module
                            Attribute:namespace:custom
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

  @Test
  public void testXInclude() {
    runInEdtAndWait(() -> {
      prepareFile("pluginWithXInclude-extensionPoints.xml");
      prepareFile("pluginWithXInclude-main.xml");
      prepareFile("pluginWithXInclude.xml");
    });
    codeInsightFixture.get().testHighlighting("pluginWithXInclude.xml");
  }

  @Test
  public void testIncludedActions() {
    runInEdtAndWait(() -> {
      prepareFile("XIncludeWithActions.xml");
      DomFileElement<IdeaPlugin> element = prepare("XIncludeWithActions-main.xml", IdeaPlugin.class);

      XmlTag[] tags = element.getRootTag().getSubTags();
      assertEquals(2, tags.length);
      XmlTag included = assertInstanceOf(IncludedXmlTag.class, tags[0]);
      assertEquals("actions", included.getName());

      List<? extends Actions> actions = element.getRootElement().getActions();
      assertEquals(2, actions.size());

      assertNotNull(actions.get(1).getXmlTag());
      Action action = actions.get(1).getGroups().get(0).getActions().get(0);
      DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(action.getId());
      assertNotNull(handler.getStub());

      assertNotNull(DomTarget.getTarget(action));
    });
  }

  @Test
  public void testStubIndexingThreadDoesNotLeaveExtensionsEmptyForEveryone() throws Exception {
    runInEdtAndWait(() -> {
      CodeInsightTestFixture fixture = codeInsightFixture.get();
      XmlFile file = prepareFile("pluginXmlStubs.xml");
      fixture.configureFromExistingVirtualFile(file.getVirtualFile());

      DomManager manager = DomManager.getDomManager(fixture.getProject());

      for (int i = 0; i < 10; i++) {
        fixture.type(' ');
        PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();

        Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
          for (XmlTag tag : SyntaxTraverser.psiTraverser(file).filter(XmlTag.class)) {
            assertNotNull(manager.getDomElement(tag), tag.getText());
          }
        }));

        // index the file
        DomFileElement<IdeaPlugin> ideaPlugin = manager.getFileElement(file, IdeaPlugin.class);
        assertFalse(DomElementClassIndex.getInstance().hasStubElementsOfType(ideaPlugin, ProductDescriptor.class));

        future.get(20, TimeUnit.SECONDS);
      }
    });
  }

  private void doBuilderTest(@TestDataFile String filePath, String stubText) {
    runInEdtAndWait(() -> DomStubTestUtil.doBuilderTest(filePath, stubText, codeInsightFixture.get()));
  }

  private <T extends DomElement> DomFileElement<T> prepare(@TestDataFile String path, Class<T> domClass) {
    return DomStubTestUtil.prepare(path, domClass, codeInsightFixture.get());
  }

  private XmlFile prepareFile(@TestDataFile String path) {
    return DomStubTestUtil.prepareFile(path, codeInsightFixture.get());
  }
}

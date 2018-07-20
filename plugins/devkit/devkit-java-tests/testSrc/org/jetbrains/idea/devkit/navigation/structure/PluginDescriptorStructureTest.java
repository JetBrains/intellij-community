// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;

@TestDataPath("$CONTENT_ROOT/testData/navigation/structure")
public class PluginDescriptorStructureTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "navigation/structure";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(Attribute.class));
    moduleBuilder.addLibrary("jblist", PathUtil.getJarPathForClass(JBList.class));
  }


  public void testPluginDescriptorStructure() {
    myFixture.addClass("package a.b;" +
                       "" +
                       "import com.intellij.util.xmlb.annotations.Attribute; " +
                       "" +
                       "public class MockServiceDescriptor { " +
                       "  @Attribute(\"serviceImplementation\")" +
                       "  public String serviceImplementation; " +
                       "}");

    VirtualFile file = myFixture.copyFileToProject("plugin.xml");
    myFixture.openFileInEditor(file);

    myFixture.testStructureView(component -> {
      TreeElementWrapper root = (TreeElementWrapper)TreeUtil.getUserObject(component.getTree().getModel().getRoot());
      TreeElement[] topLevelNodes = root.getValue().getChildren();
      assertSize(13, topLevelNodes);

      String[] expectedTopLevelNames = new String[] {"ID", "Name", "Version", "Vendor", "Description", "Change Notes",
        "Depends", "Depends", "IDEA Version", "Extensions", "Extension Points", "Application Components", "Actions"};
      String[] actualTopLevelNames = Stream.of(topLevelNodes)
        .map(treeElement -> treeElement.getPresentation().getPresentableText())
        .toArray(String[]::new);
      assertArrayEquals(expectedTopLevelNames, actualTopLevelNames);

      String[] expectedTopLevelLocations = new String[] {"plugin.id", "MyPlugin", "1.0", "YourCompany", null, null,
        "com.intellij.java-i18n", "com.intellij.javaee [optional]", "125.5 - 130.0", "plugin.id", "plugin.id", null, null};
      String[] actualTopLevelLocations = Stream.of(topLevelNodes)
        .map(treeElement -> treeElement.getPresentation().getLocationString())
        .toArray(String[]::new);
      assertArrayEquals(expectedTopLevelLocations, actualTopLevelLocations);

      TreeElement[] extensionNodes = topLevelNodes[9].getChildren();
      assertSize(5, extensionNodes);
      String[] expectedExtensionNames = new String[] {"Tool Window", "Project Configurable", "File Editor Provider",
        "Mock Application Service", "DOM | Extender"};
      String[] actualExtensionNames = Stream.of(extensionNodes)
        .map(treeElement -> treeElement.getPresentation().getPresentableText())
        .toArray(String[]::new);
      assertArrayEquals(expectedExtensionNames, actualExtensionNames);

      String[] expectedExtensionLocations = new String[] {"someToolWindow", "SomeConfigurable", "SomeFileEditorProvider",
        "SomeApplicationService", "DomExtenderId"};
      String[] actualExtensionLocations = Stream.of(extensionNodes)
        .map(treeElement -> treeElement.getPresentation().getLocationString())
        .toArray(String[]::new);
      assertArrayEquals(expectedExtensionLocations, actualExtensionLocations);

      TreeElement[] epNodes = topLevelNodes[10].getChildren();
      assertSize(6, epNodes);

      String[] expectedEpNames = new String[] {"someExtensionPoint", "toolWindow", "projectConfigurable", "fileEditorProvider",
        "mockApplicationService", "dom.extender"};
      String[] actualEpNames = Stream.of(epNodes)
        .map(treeElement -> treeElement.getPresentation().getPresentableText())
        .toArray(String[]::new);
      assertArrayEquals(expectedEpNames, actualEpNames);

      String[] expectedEpLocations = new String[] {"MyExtensionPointClass", "ToolWindowEP", "ConfigurableEP",
        "FileEditorProvider", "MockServiceDescriptor", "DomExtenderEP"};
      String[] actualEpLocations = Stream.of(epNodes)
        .map(treeElement -> treeElement.getPresentation().getLocationString())
        .toArray(String[]::new);
      assertArrayEquals(expectedEpLocations, actualEpLocations);

      TreeElement applicationComponentNode = assertOneElement(topLevelNodes[11].getChildren());
      assertEquals("Component", applicationComponentNode.getPresentation().getPresentableText());
      assertEquals("SomeApplicationComponentImplementation", applicationComponentNode.getPresentation().getLocationString());

      assertSize(2, applicationComponentNode.getChildren());
      TreeElement interfaceClass = applicationComponentNode.getChildren()[0];
      assertEquals("Interface Class", interfaceClass.getPresentation().getPresentableText());
      assertEquals("com.jetbrains.test.SomeApplicationComponentInterface", interfaceClass.getPresentation().getLocationString());
      TreeElement implementationClass = applicationComponentNode.getChildren()[1];
      assertEquals("Implementation Class", implementationClass.getPresentation().getPresentableText());
      assertEquals("com.jetbrains.test.SomeApplicationComponentImplementation", implementationClass.getPresentation().getLocationString());

      TreeElement[] actionNodes = topLevelNodes[12].getChildren();
      assertSize(2, actionNodes);
      TreeElement groupNode = actionNodes[0];
      assertEquals("Group", groupNode.getPresentation().getPresentableText());
      assertEquals("MyPlugin.MyGroup", groupNode.getPresentation().getLocationString());
      assertEquals("SomeAction2", actionNodes[1].getPresentation().getPresentableText());
      assertEquals("SomeAction2Class", actionNodes[1].getPresentation().getLocationString());

      TreeElement[] inGroupNodes = groupNode.getChildren();
      assertSize(2, inGroupNodes);
      assertEquals("SomeAction1", inGroupNodes[0].getPresentation().getPresentableText());
      assertEquals("SomeAction1Class", inGroupNodes[0].getPresentation().getLocationString());
      assertEquals(AllIcons.Actions.Back, inGroupNodes[0].getPresentation().getIcon(false));
      assertEquals("Add To Group", inGroupNodes[1].getPresentation().getPresentableText());
      assertEquals("MainMenu", inGroupNodes[1].getPresentation().getLocationString());
    });
  }
}

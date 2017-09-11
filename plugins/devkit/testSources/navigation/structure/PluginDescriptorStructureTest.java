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
package org.jetbrains.idea.devkit.navigation.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent.StructureViewTreeElementWrapper;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;

@TestDataPath("$CONTENT_ROOT/testData/navigation/structure")
public class PluginDescriptorStructureTest extends CodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/navigation/structure";
  }

  public void testPluginDescriptorStructure() {
    VirtualFile file = myFixture.copyFileToProject("plugin.xml");
    myFixture.openFileInEditor(file);
    myFixture.testStructureView(component -> {
      StructureViewTreeElementWrapper root = (StructureViewTreeElementWrapper)component.getTreeStructure().getRootElement();
      TreeElement[] topLevelNodes = root.getValue().getChildren();
      assertSize(11, topLevelNodes);

      String[] expectedTopLevelNames = new String[] {"ID", "Name", "Version", "Vendor", "Description", "Change Notes", "IDEA Version",
        "Extensions", "Extension Points", "Application Components", "Actions"};
      String[] actualTopLevelNames = Stream.of(topLevelNodes)
        .map(treeElement -> treeElement.getPresentation().getPresentableText())
        .toArray(String[]::new);
      assertArrayEquals(expectedTopLevelNames, actualTopLevelNames);

      String[] expectedTopLevelLocations = new String[] {
        "plugin.id", "MyPlugin", "1.0", "YourCompany", null, null, "125.5-130.0", "com.intellij", null, null, null};
      String[] actualTopLevelLocations = Stream.of(topLevelNodes)
        .map(treeElement -> treeElement.getPresentation().getLocationString())
        .toArray(String[]::new);
      assertArrayEquals(expectedTopLevelLocations, actualTopLevelLocations);

      TreeElement[] extensionNodes = topLevelNodes[7].getChildren();
      assertSize(6, extensionNodes);
      String[] expectedExtensionNames = new String[] {"Tool Window", "Project Configurable", "File Editor Provider",
        "Application Service", "Application Service", "DOM | Extender"};
      String[] actualExtensionNames = Stream.of(extensionNodes)
        .map(treeElement -> treeElement.getPresentation().getPresentableText())
        .toArray(String[]::new);
      assertArrayEquals(expectedExtensionNames, actualExtensionNames);

      String[] expectedExtensionLocations = new String[] {"someToolWindow", "someConfigurable", "SomeFileEditorProvider",
        "SomeApplicationService", "SomeApplicationServiceInterface", "DomExtenderId"};
      String[] actualExtensionLocations = Stream.of(extensionNodes)
        .map(treeElement -> treeElement.getPresentation().getLocationString())
        .toArray(String[]::new);
      assertArrayEquals(expectedExtensionLocations, actualExtensionLocations);

      TreeElement[] epNodes = topLevelNodes[8].getChildren();
      assertSize(1, epNodes);
      assertEquals("someExtensionPoint", epNodes[0].getPresentation().getPresentableText());
      assertEquals("MyExtensionPointClass", epNodes[0].getPresentation().getLocationString());

      TreeElement[] applicationComponentNodes = topLevelNodes[9].getChildren();
      assertSize(1, applicationComponentNodes);
      assertEquals("Component", applicationComponentNodes[0].getPresentation().getPresentableText());
      assertEquals("SomeApplicationComponent", applicationComponentNodes[0].getPresentation().getLocationString());

      TreeElement[] actionNodes = topLevelNodes[10].getChildren();
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

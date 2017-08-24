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
package com.intellij.ide.util.treeView;

import com.intellij.ui.tree.TreeSmartSelectProvider;
import com.intellij.util.ui.tree.TreeUtil;

/**
 * @author Konstantin Bulenkov
 */
public class TreeSmartSelectTest extends AbstractTreeBuilderTest {
  TreeSmartSelectProvider myProvider = new TreeSmartSelectProvider();
  public TreeSmartSelectTest() {
    super(true);
  }

  public void testSelectionIncrease() throws Exception {
    doAndWaitForBuilder(() -> {
                          Node intellij = myRoot.addChild("com").addChild("intellij");
                          intellij.addChild("a");
                          intellij.addChild("b");
                          Node c = intellij.addChild("c");
                          TreeUtil.expandAll(myTree);
                          getBuilder().select(c.myElement);
                        });
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   a\n" +
               "   b\n" +
               "   [c]\n");

    myProvider.increaseSelection(myTree);
    assertTree("-/\n" +
               " -com\n" +
               "  -intellij\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n");

    myProvider.increaseSelection(myTree);
    assertTree("-/\n" +
               " -com\n" +
               "  -[intellij]\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n");

    myProvider.increaseSelection(myTree);
    assertTree("-/\n" +
               " -[com]\n" +
               "  -[intellij]\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n");

    myProvider.increaseSelection(myTree);
    assertTree("-/\n" +
               " -[com]\n" +
               "  -[intellij]\n" +
               "   [a]\n" +
               "   [b]\n" +
               "   [c]\n");
  }

  public void testSelectionDoesntJumpTooQuickly() throws Exception {
    doAndWaitForBuilder(() -> {
      Node ktor = myRoot.addChild("ktor");
      ktor.addChild("ktor-core");
      Node ktorFeatures = ktor.addChild("ktor-features");
      ktorFeatures.addChild("jetty-http-client");
      Node locations = ktorFeatures.addChild("ktor-locations");
      Node src = locations.addChild("src");
      Node node = src.addChild("asdsd.asdas.asdas");
      node.addChild("a");
      node.addChild("b");
      Node c = node.addChild("c");

      Node test = locations.addChild("tests");
      test.addChild("fooo");
      locations.addChild("zar.txt");
      locations.addChild("zoo.txt");
      TreeUtil.expandAll(myTree);
      getBuilder().select(c.myElement);
    });

    myProvider.increaseSelection(myTree);
    myProvider.increaseSelection(myTree);
    myProvider.increaseSelection(myTree);
    myProvider.increaseSelection(myTree);
    myProvider.increaseSelection(myTree);
    assertTree("-/\n" +
               " -ktor\n" +
               "  ktor-core\n" +
               "  -ktor-features\n" +
               "   jetty-http-client\n" +
               "   -[ktor-locations]\n" +
               "    -[src]\n" +
               "     -[asdsd.asdas.asdas]\n" +
               "      [a]\n" +
               "      [b]\n" +
               "      [c]\n" +
               "    -[tests]\n" +
               "     [fooo]\n" +
               "    [zar.txt]\n" +
               "    [zoo.txt]\n");
  }

}

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
package com.intellij.structureView;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.ui.Queryable;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class SmartTreeStructureTest extends BasePlatformTestCase {
  private final Queryable.PrintInfo myPrintInfo = new Queryable.PrintInfo();
  private TestTreeModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TestTreeModel.StringTreeElement root = new TestTreeModel.StringTreeElement("root");
    myModel = new TestTreeModel(root);
    root.addChild("abc").addChild("abc_de");
    root.addChild("bcd").addChild("bhg_yt");
    root.addChild("ade").addChild("aed");
    root.addChild("bft").addChild("ttt");
    root.addChild("xxx");
    root.addChild("eed").addChild("zzz");
    root.addChild("xxx").addChild("aaa").addChild("bbb");
  }

  public void testGrouping() {
    assertStructureEqual("""
                           root
                           .Group:a
                           ..Group:d
                           ...ade
                           ....Group:a
                           .....Group:d
                           ......aed
                           ..abc
                           ...Group:a
                           ....Group:d
                           .....abc_de
                           .Group:b
                           ..Group:d
                           ...bcd
                           ....Group:b
                           .....bhg_yt
                           ..Group:f
                           ...bft
                           ....ttt
                           .Group:d
                           ..eed
                           ...zzz
                           .xxx
                           .xxx
                           ..Group:a
                           ...aaa
                           ....Group:b
                           .....bbb
                           """, PlatformTestUtil.createComparator(myPrintInfo));
  }

  public void testFiltering() {
    myModel.addFlter(new Filter() {
      @Override
      @NotNull
      public ActionPresentation getPresentation() {
        throw new RuntimeException();
      }

      @Override
      @NotNull
      public String getName() {
        throw new RuntimeException();
      }

      @Override
      public boolean isVisible(TreeElement treeNode) {
        return !treeNode.toString().contains("a");
      }

      @Override
      public boolean isReverted() {
        return false;
      }
    });

    assertStructureEqual("""
                           root
                           .Group:b
                           ..Group:d
                           ...bcd
                           ....Group:b
                           .....bhg_yt
                           ..Group:f
                           ...bft
                           ....ttt
                           .Group:d
                           ..eed
                           ...zzz
                           .xxx
                           .xxx
                           """, PlatformTestUtil.createComparator(myPrintInfo));
  }

  public void testSorting() {
    myModel.addSorter(new Sorter() {
      @NotNull
      @Override
      public Comparator getComparator() {
        return (o1, o2) -> {
          if (o1 instanceof Group && !(o2 instanceof Group)) return -1;
          if (!(o1 instanceof Group) && o2 instanceof Group) return 1;
          return 0;
        };
      }

      @Override
      public boolean isVisible() {
        return true;
      }

      @Override
      @NotNull
      public ActionPresentation getPresentation() {
        throw new RuntimeException();
      }

      @Override
      @NotNull
      public String getName() {
        throw new RuntimeException();
      }
    });

    myModel.addSorter(new Sorter() {
      @NotNull
      @Override
      public Comparator getComparator() {
        return Comparator.comparing(Object::toString);
      }

      @Override
      public boolean isVisible() {
        return true;
      }

      @Override
      @NotNull
      public ActionPresentation getPresentation() {
        throw new RuntimeException();
      }

      @Override
      @NotNull
      public String getName() {
        throw new RuntimeException();
      }
    });

    assertStructureEqual("""
                           root
                           .Group:a
                           ..Group:d
                           ...ade
                           ....Group:a
                           .....Group:d
                           ......aed
                           ..abc
                           ...Group:a
                           ....Group:d
                           .....abc_de
                           .Group:b
                           ..Group:d
                           ...bcd
                           ....Group:b
                           .....bhg_yt
                           ..Group:f
                           ...bft
                           ....ttt
                           .Group:d
                           ..eed
                           ...zzz
                           .xxx
                           .xxx
                           ..Group:a
                           ...aaa
                           ....Group:b
                           .....bbb
                           """, null);
  }

  public void testUnsorted(){
    TestTreeModel.StringTreeElement root = new TestTreeModel.StringTreeElement("root");
    myModel = new TestTreeModel(root);
    root.addChild("xxx");
    root.addChild("bbb");
    root.addChild("bbb");
    root.addChild("zzz").addChild("ttt");
    root.addChild("aaa");
    root.addChild("aaa").addChild("zzz");
    root.addChild("xxx");
    assertStructureEqual("""
                           root
                           .xxx
                           .Group:b
                           ..bbb
                           ..bbb
                           .zzz
                           ..ttt
                           .Group:a
                           ..aaa
                           ..aaa
                           ...zzz
                           .xxx
                           """, null);
  }

  private void assertStructureEqual(@NonNls String expected, @Nullable Comparator comparator) {
    SmartTreeStructure structure = new SmartTreeStructure(myFixture.getProject(), myModel);
    String actual = PlatformTestUtil.print(structure, structure.getRootElement(), 0, comparator, -1, '.', null).toString();
    assertEquals(expected, actual);
  }
}

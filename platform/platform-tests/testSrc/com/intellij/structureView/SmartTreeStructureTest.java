package com.intellij.structureView;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class SmartTreeStructureTest extends LightPlatformCodeInsightFixtureTestCase {
  private TestTreeModel myModel;

  public SmartTreeStructureTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TestTreeModel.StringTreeElement root = new TestTreeModel.StringTreeElement("root");
    myModel = new TestTreeModel(root);
    root.addChild("abc").addChild("abcde");
    root.addChild("bcd").addChild("bhgyt");
    root.addChild("ade").addChild("aed");
    root.addChild("bft").addChild("ttt");
    root.addChild("xxx");
    root.addChild("eed").addChild("zzz");
    root.addChild("xxx").addChild("aaa").addChild("bbb");

  }

  public void testGrouping() throws Exception {
    assertStructureEqual("root\n" +
                         ".Group:a\n" +
                         "..Group:d\n" +
                         "...ade\n" +
                         "....Group:a\n" +
                         ".....Group:d\n" +
                         "......aed\n" +
                         "..abc\n" +
                         "...Group:a\n" +
                         "....Group:d\n" +
                         ".....abcde\n" +
                         ".Group:b\n" +
                         "..Group:d\n" +
                         "...bcd\n" +
                         "....Group:b\n" +
                         ".....bhgyt\n" +
                         "..Group:f\n" +
                         "...bft\n" +
                         "....ttt\n" +
                         ".Group:d\n" +
                         "..eed\n" +
                         "...zzz\n" +
                         ".xxx\n" +
                         ".xxx\n" +
                         "..Group:a\n" +
                         "...aaa\n" +
                         "....Group:b\n" +
                         ".....bbb\n", PlatformTestUtil.DEFAULT_COMPARATOR);


  }

  public void testFiltering() throws Exception {
    myModel.addFlter(new Filter(){
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

    assertStructureEqual("root\n" +
                         ".Group:b\n" +
                         "..Group:d\n" +
                         "...bcd\n" +
                         "....Group:b\n" +
                         ".....bhgyt\n" +
                         "..Group:f\n" +
                         "...bft\n" +
                         "....ttt\n" +
                         ".Group:d\n" +
                         "..eed\n" +
                         "...zzz\n" +
                         ".xxx\n" +
                         ".xxx\n", PlatformTestUtil.DEFAULT_COMPARATOR);

  }

  public void testSorting() throws Exception {


    myModel.addSorter(new Sorter() {
      @Override
      public Comparator getComparator() {
        return new Comparator() {
          @Override
          public int compare(Object o1, Object o2) {
            if (o1 instanceof Group && !(o2 instanceof Group)) return -1;
            if (!(o1 instanceof Group) && o2 instanceof Group) return 1;
            return 0;
          }
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
      @Override
      public Comparator getComparator() {
        return new Comparator() {
          @Override
          public int compare(Object o1, Object o2) {
            return o1.toString().compareTo(o2.toString());
          }
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

    assertStructureEqual("root\n" +
                     ".Group:a\n" +
                     "..Group:d\n" +
                     "...ade\n" +
                     "....Group:a\n" +
                     ".....Group:d\n" +
                     "......aed\n" +
                     "..abc\n" +
                     "...Group:a\n" +
                     "....Group:d\n" +
                     ".....abcde\n" +
                     ".Group:b\n" +
                     "..Group:d\n" +
                     "...bcd\n" +
                     "....Group:b\n" +
                     ".....bhgyt\n" +
                     "..Group:f\n" +
                     "...bft\n" +
                     "....ttt\n" +
                     ".Group:d\n" +
                     "..eed\n" +
                     "...zzz\n" +
                     ".xxx\n" +
                     ".xxx\n" +
                     "..Group:a\n" +
                     "...aaa\n" +
                     "....Group:b\n" +
                     ".....bbb\n"
                         , null);

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
    assertStructureEqual("root\n" +
                         ".xxx\n" +
                         ".Group:b\n" +
                         "..bbb\n" +
                         "..bbb\n" +
                         ".zzz\n" +
                         "..ttt\n" +
                         ".Group:a\n" +
                         "..aaa\n" +
                         "..aaa\n" +
                         "...zzz\n" +
                         ".xxx\n", null);

  }

  private void assertStructureEqual(@NonNls String expected, Comparator comparator) {
    SmartTreeStructure structure = new SmartTreeStructure(myFixture.getProject(), myModel);

    String actual = PlatformTestUtil.print(structure, structure.getRootElement(), 0, comparator, -1, '.', null).toString();


    assertEquals(expected, actual);
  }

}

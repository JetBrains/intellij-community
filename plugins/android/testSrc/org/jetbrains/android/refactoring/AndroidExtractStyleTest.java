package org.jetbrains.android.refactoring;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidExtractStyleTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/extractStyle/";

  public void test1() {
    doTest("myStyle", new String[]{"android:textColor", "android:background"},
           new String[]{"android:layout_width", "android:layout_height", "android:textColor", "android:background"}, false);
  }

  public void test2() {
    doTest("style1.s", new String[]{"android:textColor", "android:background"}, true);
  }

  public void test3() {
    doTest("style2", new String[]{"android:textColor", "android:background"}, true);
  }

  public void test4() {
    doTest("style1.s", new String[]{"android:textColor", "android:background"}, false);
  }

  public void test5() {
    doTestDisabled();
  }

  public void test6() {
    doTestDisabled();
  }

  public void test7() {
    doTest("myStyle", new String[]{"android:textColor", "android:background"}, false);
  }

  public void test8() {
    doTestHidden();
  }

  private void doTestHidden() {
    final String testName = getTestName(true);
    final VirtualFile f =
      myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/values/test" + testName + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    final Presentation presentation = myFixture.testAction(new AndroidExtractStyleAction());
    assertFalse(presentation.isVisible());
  }

  private void doTestDisabled() {
    final String testName = getTestName(true);
    final VirtualFile f =
      myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test" + testName + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    final Presentation presentation = myFixture.testAction(new AndroidExtractStyleAction());
    assertFalse(presentation.isEnabled());
    assertTrue(presentation.isVisible());
  }

  private void doTest(@NotNull String styleName,
                      @NotNull String[] attributes,
                      boolean copyInitialStylesXml) {
    doTest(styleName, attributes, attributes, copyInitialStylesXml);
  }

  private void doTest(@NotNull String styleName,
                      @NotNull String[] attributesToExtract,
                      @NotNull String[] expectedExtractableAttrs,
                      boolean copyInitialStylesXml) {
    final String testName = getTestName(true);

    if (copyInitialStylesXml) {
      myFixture.copyFileToProject(BASE_PATH + testName + "_styles.xml", "res/values/styles.xml");
    }
    final VirtualFile f =
      myFixture.copyFileToProject(BASE_PATH + testName + ".xml", "res/layout/test" + testName + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.testAction(new AndroidExtractStyleAction(new MyConfig(myModule, styleName, attributesToExtract, expectedExtractableAttrs)));
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.xml");
    myFixture.checkResultByFile("res/values/styles.xml", BASE_PATH + testName + "_styles_after.xml", true);
  }

  private static class MyConfig extends AndroidExtractStyleAction.MyTestConfig {
    private final String[] myExpectedExtractableAttributes;

    MyConfig(@NotNull Module module,
             @NotNull String styleName,
             @NotNull String[] attributesToExtract,
             @NotNull String[] expectedExtractableAttributes) {
      super(module, styleName, attributesToExtract);
      myExpectedExtractableAttributes = expectedExtractableAttributes;
      Arrays.sort(myExpectedExtractableAttributes);
    }

    @Override
    public void validate(@NotNull List<XmlAttribute> extractableAttributes) {
      final List<String> names = new ArrayList<String>(extractableAttributes.size());

      for (XmlAttribute attribute : extractableAttributes) {
        names.add(attribute.getName());
      }
      final String[] extractableAttributeNames = ArrayUtil.toStringArray(names);
      Arrays.sort(extractableAttributeNames);

      assertTrue(
        "Expected: " + Arrays.toString(myExpectedExtractableAttributes) + "\nActual: " + Arrays.toString(extractableAttributeNames),
        Arrays.equals(myExpectedExtractableAttributes, extractableAttributeNames)
      );
    }
  }
}

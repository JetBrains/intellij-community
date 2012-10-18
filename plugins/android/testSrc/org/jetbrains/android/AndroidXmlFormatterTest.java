package org.jetbrains.android;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings;
import org.jetbrains.android.formatter.AndroidXmlPredefinedCodeStyle;

import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlFormatterTest extends AndroidTestCase {
  private static final String BASE_PATH = "formatter/xml/";

  private CodeStyleSettings mySettings;

  public AndroidXmlFormatterTest() {
    super(false);
  }

  public void testLayout1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestLayout("layout1.xml");
  }

  public void testLayout2() throws Exception {
    doTestLayout("layout1.xml");
  }

  public void testLayout3() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_KEEP_BLANK_LINES = 0;
    doTestLayout("layout1.xml");
  }

  public void testLayout4() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = true;
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.LAYOUT_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    androidSettings.LAYOUT_SETTINGS.INSERT_BLANK_LINE_BEFORE_TAG = false;
    doTestLayout("layout1.xml");
  }

  public void testLayout5() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.LAYOUT_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTestLayout("layout1.xml");
  }

  public void testLayout6() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = false;
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.LAYOUT_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    androidSettings.LAYOUT_SETTINGS.INSERT_BLANK_LINE_BEFORE_TAG = false;
    doTestLayout("layout1.xml");
  }

  public void testManifest1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestManifest("manifest1.xml");
  }

  public void testManifest2() throws Exception {
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTestManifest("manifest1.xml");
  }

  public void testManifest3() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    xmlSettings.XML_KEEP_BLANK_LINES = 0;
    doTestManifest("manifest1.xml");
  }

  public void testManifest4() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = true;
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.MANIFEST_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    doTestManifest("manifest1.xml");
  }

  public void testManifest5() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.MANIFEST_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTestManifest("manifest1.xml");
  }

  public void testManifest6() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.MANIFEST_SETTINGS.GROUP_TAGS_WITH_SAME_NAME = false;
    doTestManifest("manifest1.xml");
  }

  public void testManifest7() throws Exception {
    final XmlCodeStyleSettings xmlSettings = mySettings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    doTestManifest("manifest1.xml");
  }

  public void testValues1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestValues("values1.xml");
  }

  public void testValues2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    doTestValues("values1.xml");
  }

  public void testValues3() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTestValues("values1.xml");
  }

  public void testValues4() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTestValues("values4.xml");
  }

  public void testValues5() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.INSERT_LINE_BREAKS_AROUND_STYLE = false;
    doTestValues("values4.xml");
  }

  public void testSelector1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("selector1.xml", "res/drawable/selector.xml");
  }

  public void testSelector2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("selector2.xml", "res/color/selector.xml");
  }

  public void testSelector3() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
    doTest("selector2.xml", "res/color/selector.xml");
  }

  public void testShapeDrawable1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("shapeDrawable1.xml", "res/drawable/drawable.xml");
  }

  public void testShapeDrawable2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.OTHER_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTest("shapeDrawable1.xml", "res/drawable/drawable.xml");
  }

  public void testPreferences1() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    doTest("preferences1.xml", "res/xml/preferences.xml");
  }

  public void testPreferences2() throws Exception {
    new AndroidXmlPredefinedCodeStyle().apply(mySettings);
    final AndroidXmlCodeStyleSettings androidSettings = mySettings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
    androidSettings.OTHER_SETTINGS.WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
    doTest("preferences1.xml", "res/xml/preferences.xml");
  }

  private void doTestLayout(String fileName) throws IOException {
    createManifest();
    doTest(fileName, "res/layout/layout.xml");
  }

  private void doTestManifest(String fileName) {
    doTest(fileName, "AndroidManifest.xml");
  }

  private void doTestValues(String fileName) {
    doTest(fileName, "res/values/values.xml");
  }

  private void doTest(String fileName, String dstFileName) {
    final VirtualFile f = myFixture.copyFileToProject(BASE_PATH + fileName, dstFileName);
    myFixture.configureFromExistingVirtualFile(f);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile());
      }
    });
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(mySettings);
  }

  @Override
  public void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    super.tearDown();
  }
}

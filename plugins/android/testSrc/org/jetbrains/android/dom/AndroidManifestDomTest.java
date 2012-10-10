package org.jetbrains.android.dom;

import com.android.sdklib.SdkConstants;
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;

/**
 * @author coyote
 */
public class AndroidManifestDomTest extends AndroidDomTest {
  public AndroidManifestDomTest() {
    super(false, "dom/manifest");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return SdkConstants.FN_ANDROID_MANIFEST_XML;
  }

  public void testAttributeNameCompletion1() throws Throwable {
    doTestCompletionVariants("an1.xml", "android:icon", "android:label", "android:priority");
  }

  public void testAttributeNameCompletion2() throws Throwable {
    doTestCompletionVariants("an2.xml", "debuggable", "description", "hasCode");
  }

  public void testAttributeNameCompletion3() throws Throwable {
    toTestCompletion("an3.xml", "an3_after.xml");
  }

  public void testAttributeNameCompletion4() throws Throwable {
    toTestCompletion("an4.xml", "an4_after.xml");
  }

  public void testAttributeByLocalNameCompletion() throws Throwable {
    toTestCompletion("attrByLocalName.xml", "attrByLocalName_after.xml");
  }

  public void testTagNameCompletion2() throws Throwable {
    doTestCompletionVariants("tn2.xml", "manifest");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting2() throws Throwable {
    doTestHighlighting("hl2.xml");
  }

  public void testTagNameCompletion3() throws Throwable {
    toTestCompletion("tn3.xml", "tn3_after.xml");
  }

  public void testTagNameCompletion4() throws Throwable {
    toTestCompletion("tn4.xml", "tn4_after.xml");
  }

  public void testAttributeValueCompletion1() throws Throwable {
    doTestCompletionVariants("av1.xml", "behind", "landscape", "nosensor", "portrait", "sensor", "unspecified", "user");
  }

  public void testResourceCompletion1() throws Throwable {
    doTestCompletionVariants("av2.xml", "@android:", "@style/style1");
  }

  public void testResourceCompletion2() throws Throwable {
    doTestCompletionVariants("av3.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                           "@string/itStr");
  }

  public void testResourceCompletion3() throws Throwable {
    doTestCompletionVariants("av4.xml", "@android:", "@anim/", "@color/", "@dimen/", "@drawable/", "@id/", "@string/", "@style/");
  }

  public void testTagNameCompletion1() throws Throwable {
    doTestCompletionVariants("tn1.xml", "uses-permission", "uses-sdk", "uses-configuration");
  }

  public void testSoftTagsAndAttrs() throws Throwable {
    myFixture.disableInspections(new AndroidUnknownAttributeInspection());
    myFixture.disableInspections(new AndroidElementNotAllowedInspection());
    doTestHighlighting("soft.xml");
  }

  public void testUnknownAttribute() throws Throwable {
    doTestHighlighting("unknownAttribute.xml");
  }

  /*public void testNamespaceCompletion() throws Throwable {
    toTestCompletion("ns.xml", "ns_after.xml");
  }*/

  public void testInnerActivityHighlighting() throws Throwable {
    copyFileToProject("A.java", "src/p1/p2/A.java");
    doTestHighlighting(getTestName(false) + ".xml");
  }

  public void testInnerActivityCompletion() throws Throwable {
    copyFileToProject("A.java", "src/p1/p2/A.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "B");
  }

  public void testActivityCompletion1() throws Throwable {
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    copyFileToProject("MyActivity2.java", "src/p1/MyActivity2.java");
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletionVariants(getTestName(false) + ".xml", ".MyActivity", ".p3.MyActivity1", "p1.MyActivity2");
  }

  public void testActivityCompletion2() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "p2.MyActivity");
  }

  public void testActivityCompletion3() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    doTestCompletionVariants(getTestName(false) + ".xml", ".MyActivity", ".p3.MyActivity1");
  }

  public void testActivityCompletion4() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "MyActivity", "p3.MyActivity1");
  }

  public void testActivityCompletion5() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    copyFileToProject("MyActivity1.java", "src/p1/p2/p3/MyActivity1.java");
    copyFileToProject("MyActivity2.java", "src/p1/MyActivity2.java");
    doTestCompletionVariants(getTestName(false) + ".xml", "MyActivity", "p3.MyActivity1");
  }

  public void testActivityCompletion6() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletion(false);
  }

  public void testBackupAgentCompletion() throws Throwable {
    copyFileToProject("MyBackupAgent.java", "src/p1/p2/MyBackupAgent.java");
    doTestCompletionVariants(getTestName(false) + ".xml", ".MyBackupAgent");
  }

  public void testUsesPermissionCompletion() throws Throwable {
    doTestCompletion(false);
  }

  public void testApplicationNameCompletion() throws Throwable {
    copyFileToProject("MyApplication.java", "src/p1/p2/MyApplication.java");
    doTestCompletion(false);
  }

  public void testManageSpaceActivity() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestCompletion(false);
  }

  public void testInstrumentationRunner() throws Throwable {
    doTestHighlighting(getTestName(false) + ".xml");
  }

  public void testIntentsCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testIntentsCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testCompletionInManifestTag() throws Throwable {
    doTestCompletion();
  }

  public void testActivityAlias() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testActivityAlias1() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testActivityAlias2() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testActivityAlias3() throws Throwable {
    copyFileToProject("MyActivity.java", "src/p1/p2/MyActivity.java");
    doTestHighlighting();
  }

  public void testIntegerRefAsEnumValue() throws Throwable {
    copyFileToProject("myIntResource.xml", "res/values/myIntResource.xml");
    doTestCompletion();
  }

  public void testJavaHighlighting() throws Throwable {
    copyFileToProject("PermissionsManifest.xml", "AndroidManifest.xml");
    copyFileToProject("Manifest.java", "src/p1/p2/Manifest.java");
    doTestJavaHighlighting("p1.p2");
  }

  public void testAndroidPrefixCompletion() throws Throwable {
    // do not complete prefix in manifest because there is not many attributes
    doTestAndroidPrefixCompletion(null);
  }

  public void testNamespaceCompletion() throws Throwable {
    doTestNamespaceCompletion(true, true);
  }

  public void testNamespaceCompletion1() throws Throwable {
    doTestNamespaceCompletion(true, false);
  }
}

package org.jetbrains.android.dom;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.AndroidDomInspection;
import org.jetbrains.android.resourceManagers.ResourceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author coyote
 */
@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
abstract class AndroidDomTest extends AndroidTestCase {
  protected final String testFolder;

  protected AndroidDomTest(boolean createManifest, String testFolder) {
    super(createManifest);
    this.testFolder = testFolder;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("dom/R.java", "gen/p1/p2/R.java");
    myFixture.enableInspections(AndroidDomInspection.class/*, AndroidUnknownAttributeInspection.class*/);
  }

  @Override
  protected String getResDir() {
    return "dom/res";
  }

  protected static String[] withNamespace(String... arr) {
    List<String> list = new ArrayList<String>();
    for (String s : arr) {
      list.add("android:" + s);
    }
    return ArrayUtil.toStringArray(list);
  }

  protected void doTestCompletionVariants(String fileName, String... variants) throws Throwable {
    VirtualFile file = copyFileToProject(fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    UsefulTestCase.assertSameElements(lookupElementStrings, variants);
  }

  protected static List<String> getAllResources() {
    List<String> list = new ArrayList<String>();
    for (String type : ResourceManager.REFERABLE_RESOURCE_TYPES) {
      list.add('@' + type + '/');
    }
    return list;
  }

  protected void doTestHighlighting() throws Throwable {
    doTestHighlighting(getTestName(true) + ".xml");
  }

  protected void doTestHighlighting(String file) throws Throwable {
    VirtualFile virtualFile = copyFileToProject(file);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(false, false, false);
  }

  protected void doTestCompletion() throws Throwable {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  protected void toTestCompletion(String fileBefore, String fileAfter) throws Throwable {
    VirtualFile file = copyFileToProject(fileBefore);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(testFolder + '/' + fileAfter);
  }

  protected abstract String getPathToCopy(String testFileName);

  protected VirtualFile copyFileToProject(String path) throws IOException {
    return copyFileToProject(path, getPathToCopy(path));
  }

  protected VirtualFile copyFileToProject(String from, String to) throws IOException {
    return myFixture.copyFileToProject(testFolder + '/' + from, to);
  }
}


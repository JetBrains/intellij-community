package org.jetbrains.android.dom;

import com.intellij.util.ArrayUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.AndroidDomInspection;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.android.resourceManagers.ResourceManager;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

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
    myFixture.enableInspections(AndroidDomInspection.class, AndroidUnknownAttributeInspection.class);
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
    String path = copyFileToProject(fileName);
    myFixture.testCompletionVariants(path, variants);
  }

  protected static List<String> getAllResources() {
    List<String> list = new ArrayList<String>();
    for (String type : ResourceManager.REFERABLE_RESOURCE_TYPES) {
      list.add('@' + type + '/');
    }
    return list;
  }

  protected void doTestHighlighting(String file) throws Throwable {
    String path = copyFileToProject(file);
    myFixture.testHighlighting(false, false, false, path);
  }

  protected void toTestCompletion(String fileBefore, String fileAfter) throws Throwable {
    String path = copyFileToProject(fileBefore);
    myFixture.testCompletion(path, testFolder + '/' + fileAfter);
  }

  protected abstract String getPathToCopy(String testFileName);

  protected String copyFileToProject(String path) throws IOException {
    return copyFileToProject(path, getPathToCopy(path));
  }

  protected String copyFileToProject(String from, String to) throws IOException {
    String pathToCopy = getPathToCopy(from);
    myFixture.copyFileToProject(testFolder + '/' + from, to);
    return pathToCopy;
  }
}


package org.jetbrains.android.dom;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.AndroidDomInspection;
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.annotations.Nullable;

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
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.enableInspections(AndroidDomInspection.class,
                                AndroidUnknownAttributeInspection.class,
                                AndroidElementNotAllowedInspection.class);
  }

  @Override
  protected String getResDir() {
    return "dom/res";
  }

  protected void doTestJavaCompletion(String aPackage) throws Throwable {
    final String fileName = getTestName(false) + ".java";
    final VirtualFile file = copyFileToProject(fileName, "src/" + aPackage.replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(testFolder + '/' + getTestName(false) + "_after.java");
  }

  protected void doTestNamespaceCompletion(boolean systemNamespace, boolean customNamespace) throws IOException {
    final VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    final List<String> variants = myFixture.getLookupElementStrings();
    assertNotNull(variants);
    final List<String> expectedVariants = new ArrayList<String>();

    if (systemNamespace) {
      expectedVariants.add("http://schemas.android.com/apk/res/android");
    }
    if (customNamespace) {
      expectedVariants.add("http://schemas.android.com/apk/res/p1.p2");
    }
    assertEquals(expectedVariants, variants);
  }

  protected void doTestCompletionVariants(String fileName, String... variants) throws Throwable {
    VirtualFile file = copyFileToProject(fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    UsefulTestCase.assertSameElements(lookupElementStrings, variants);
  }

  protected void doTestHighlighting() throws Throwable {
    doTestHighlighting(getTestName(true) + ".xml");
  }

  protected void doTestHighlighting(String file) throws Throwable {
    VirtualFile virtualFile = copyFileToProject(file);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doTestJavaHighlighting(String aPackage) throws Throwable {
    final String fileName = getTestName(false) + ".java";
    final VirtualFile virtualFile = copyFileToProject(fileName, "src/" + aPackage.replace('.', '/') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doTestCompletion() throws Throwable {
    doTestCompletion(true);
  }

  protected void doTestCompletion(boolean lowercaseFirstLetter) throws Throwable {
    toTestCompletion(getTestName(lowercaseFirstLetter) + ".xml", getTestName(lowercaseFirstLetter) + "_after.xml");
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

  protected void doTestAndroidPrefixCompletion(@Nullable String prefix) throws IOException {
    final VirtualFile f = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.complete(CompletionType.BASIC);
    List<String> strs = myFixture.getLookupElementStrings();
    if (prefix != null) {
      assertNotNull(strs);
      assertEquals(strs.get(0), prefix);
    }
    else if (strs != null && strs.size() > 0) {
      final String first = strs.get(0);
      assertFalse(first.endsWith(":"));
    }
  }
}


package org.jetbrains.android;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.android.inspections.lint.AndroidAddStringResourceQuickFix;
import org.jetbrains.android.inspections.lint.AndroidLintExternalAnnotator;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLintTest extends AndroidTestCase {
  @NonNls private static final String BASE_PATH = "/lint/";
  @NonNls private static final String BASE_PATH_GLOBAL = BASE_PATH + "global/";

  public AndroidLintTest() {
    super(false);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    AndroidLintInspectionBase.invalidateInspectionShortName2IssueMap();
  }

  public void testHardcodedQuickfix() throws Exception {
    doTestHardcodedQuickfix();
  }

  public void testHardcodedQuickfix1() throws Exception {
    doTestHardcodedQuickfix();
  }

  private void doTestHardcodedQuickfix() throws IOException {
    final IntentionAction action = doTestHighlightingAndGetQuickfix(
      new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection(),
      AndroidBundle.message("add.string.resource.intention.text"), false ? "AndroidManifest.xml" : "/res/layout/layout.xml", "xml");
    assertNotNull(action);
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

    new WriteCommandAction(myFixture.getProject(), "") {
      @Override
      protected void run(Result result) throws Throwable {
        ((AndroidAddStringResourceQuickFix)action)
          .invokeIntention(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "hello");
      }
    }.execute();

    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testContentDescription() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintContentDescriptionInspection(),
                  AndroidBundle.message("android.lint.inspections.add.content.description"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testContentDescription1() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintContentDescriptionInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testAdapterViewChildren() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintAdapterViewChildrenInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testScrollViewChildren() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintScrollViewCountInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testMissingPrefix() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingPrefixInspection(),
                  AndroidBundle.message("android.lint.inspections.add.android.prefix"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testMissingPrefix1() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintMissingPrefixInspection(),
                  AndroidBundle.message("android.lint.inspections.add.android.prefix"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testDuplicatedIds() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintDuplicateIdsInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testInefficientWeight() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintInefficientWeightInspection(),
                  AndroidBundle.message("android.lint.inspections.replace.with.zero.dp"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testBaselineWeights() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintDisableBaselineAlignmentInspection(),
                  AndroidBundle.message("android.lint.inspections.set.baseline.attribute"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testObsoleteLayoutParams() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintObsoleteLayoutParamInspection(),
                  AndroidBundle.message("android.lint.inspections.remove.attribute"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testConvertToDp() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintPxUsageInspection(),
                  AndroidBundle.message("android.lint.inspections.convert.to.dp"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testScrollViewSize() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintScrollViewSizeInspection(),
                  AndroidBundle.message("android.lint.inspections.set.to.wrap.content"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testExportedService() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintExportedServiceInspection(),
                  AndroidBundle.message("android.lint.inspections.add.permission.attribute"),
                  "AndroidManifest.xml", "xml");
  }

  public void testEditText() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTextFieldsInspection(),
                  AndroidBundle.message("android.lint.inspections.add.input.type.attribute"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testUselessLeaf() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintUselessLeafInspection(),
                  AndroidBundle.message("android.lint.inspections.remove.unnecessary.view"),
                  "/res/layout/layout.xml", "xml");
  }

  public void testUselessParent() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintUselessParentInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testTypographyDashes() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTypographyDashesInspection(),
                  AndroidBundle.message("android.lint.inspections.replace.with.suggested.characters"),
                  "/res/values/typography.xml", "xml");
  }

  public void testTypographyQuotes() throws Exception {
    doTestWithFix(new AndroidLintInspectionToolProvider.AndroidLintTypographyQuotesInspection(),
                  AndroidBundle.message("android.lint.inspections.replace.with.suggested.characters"),
                  "/res/values/typography.xml", "xml");
  }

  public void testProguard() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/proguard.cfg", "proguard.cfg");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintProguardInspection());
  }

  public void testManifestOrder() throws Exception {
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintManifestOrderInspection());
  }

  public void testButtonsOrder() throws Exception {
    myFixture.copyFileToProject(getGlobalTestDir() + "/AndroidManifest.xml", "AndroidManifest.xml");
    myFixture.copyFileToProject(getGlobalTestDir() + "/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(getGlobalTestDir() + "/layout.xml", "res/layout/layout.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintButtonOrderInspection());
  }

  public void testViewType() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    myFixture.copyFileToProject(getGlobalTestDir() + "/layout.xml", "res/layout/layout.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintWrongViewCastInspection());
  }

  public void testDuplicateIcons() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/dup1.png", "res/drawable/dup1.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/dup2.png", "res/drawable/dup2.png");
    myFixture.copyFileToProject(getGlobalTestDir() + "/other.png", "res/drawable/other.png");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintIconDuplicatesInspection());
  }

  public void testSuppressingInXml1() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testSuppressingInXml2() throws Exception {
    doTestNoFix(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection(),
                "/res/layout/layout.xml", "xml");
  }

  public void testSuppressingInXml3() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/layout.xml", "res/layout/layout.xml");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintHardcodedTextInspection());
  }

  public void testSuppressingInJava() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintUseValueOfInspection());
  }

  public void testLintInJavaFile() throws Exception {
    createManifest();
    myFixture.copyFileToProject(getGlobalTestDir() + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    doGlobalInspectionTest(new AndroidLintInspectionToolProvider.AndroidLintUseValueOfInspection());
  }

  private void doGlobalInspectionTest(@NotNull AndroidLintInspectionBase inspection) {
    final GlobalInspectionToolWrapper wrapper = new GlobalInspectionToolWrapper(inspection);
    myFixture.enableInspections(wrapper);

    final AnalysisScope scope = new AnalysisScope(myModule);
    scope.invalidate();

    final InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(getProject());
    final GlobalInspectionContextImpl globalContext =
      CodeInsightTestFixtureImpl.createGlobalContextForTool(scope, getProject(), inspectionManager, wrapper);

    InspectionTestUtil.runTool(wrapper, scope, globalContext, inspectionManager);
    InspectionTestUtil.compareToolResults(wrapper, false, getTestDataPath() + getGlobalTestDir());
  }

  private String getGlobalTestDir() {
    return BASE_PATH_GLOBAL + getTestName(true);
  }

  private void doTestNoFix(@NotNull AndroidLintInspectionBase inspection, @NotNull String copyTo, @NotNull String extension)
    throws IOException {
    doTestHighlighting(inspection, copyTo, extension);

    IntentionAction action = null;

    for (IntentionAction a : myFixture.getAvailableIntentions()) {
      if (a instanceof AndroidLintExternalAnnotator.MyFixingIntention) {
        action = a;
      }
    }
    assertNull(action);
  }

  private void doTestWithFix(@NotNull AndroidLintInspectionBase inspection,
                             @NotNull String message,
                             @NotNull String copyTo,
                             @NotNull String extension)
    throws IOException {
    final IntentionAction action = doTestHighlightingAndGetQuickfix(inspection, message, copyTo, extension);
    assertNotNull(action);
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

    new WriteCommandAction(myFixture.getProject(), "") {
      @Override
      protected void run(Result result) throws Throwable {
        action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();

    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after." + extension);
  }

  @Nullable
  private IntentionAction doTestHighlightingAndGetQuickfix(@NotNull AndroidLintInspectionBase inspection,
                                                           @NotNull String message,
                                                           @NotNull String copyTo,
                                                           @NotNull String extension) throws IOException {
    doTestHighlighting(inspection, copyTo, extension);

    IntentionAction action = null;

    for (IntentionAction a : myFixture.getAvailableIntentions()) {
      if (message.equals(a.getText())) {
        action = a;
      }
    }
    return action;
  }

  private void doTestHighlighting(@NotNull AndroidLintInspectionBase inspection, @NotNull String copyTo, @NotNull String extension)
    throws IOException {
    if (!"AndroidManifest.xml".equals(copyTo)) {
      createManifest();
    }

    myFixture.enableInspections(new GlobalInspectionToolWrapper(inspection));
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "." + extension, copyTo);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, false);
  }
}

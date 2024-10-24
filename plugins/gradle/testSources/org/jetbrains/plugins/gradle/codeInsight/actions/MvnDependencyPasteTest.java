// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

import static org.jetbrains.plugins.gradle.action.PasteMvnDependencyPreProcessor.toGradleDependency;

public class MvnDependencyPasteTest extends LightJavaCodeInsightTestCase {

  public void testGradleDependencyScope() {
    directTransformationTest("implementation 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", null, null));
    directTransformationTest("implementation 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", "compile", null));
    directTransformationTest("testImplementation 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", "test", null));
    directTransformationTest("runtime 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", "runtime", null));
    directTransformationTest("compileOnly 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", "provided", null));
  }

  public void testDependencyWithoutVersion() {
    directTransformationTest("implementation 'group:artifact'", getDependency("group", "artifact", null, null, null));
  }

  public void testKotlinDslDependency() {
    directTransformationTestKotlinDsl("implementation(\"group:artifact:1.0\")", getDependency("group", "artifact", "1.0", null, null));
  }

  public void testTrimLeadingComment() {
    String dependency = getDependency("group", "artifact", "1.0", null, null);
    String comment = "<!-- comment before dependency -->\n";
    copyPasteTest("implementation 'group:artifact:1.0'", dependency);
    copyPasteTest("implementation 'group:artifact:1.0'", comment + dependency);
  }

  public void testPastedGradleDependency() {
    copyPasteTest("runtime 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", "runtime", null));
  }

  public void testPastedKotlinDslGradleDependency() {
    copyPasteTestKotlinDsl("runtime(\"group:artifact:1.0\")", getDependency("group", "artifact", "1.0", "runtime", null));
  }

  public void testDependencyWithClassifier() {
    copyPasteTest("runtime 'group:artifact:1.0:jdk14'", getDependency("group", "artifact", "1.0", "runtime", "jdk14"));
  }

  public void test_DoNotConvertIfCoordinatesNotClear() {
    String noArtifact = getDependency("group", null, "1.0", "runtime", null);
    configureFromFileText("pom.xml", noArtifact);
    selectWholeFile();
    performCut();
    configureGradleFile(false);
    int old = CodeInsightSettings.getInstance().REFORMAT_ON_PASTE;
    try {
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;
      performPaste();
      checkResultByText(null, """
        dependencies {
            <dependency>
        <groupId>group</groupId>
        <version>1.0</version>
        <scope>runtime</scope>
        </dependency>
        }""", true);
    }
    finally {
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = old;
    }
  }

  public void test_AddCompile() {
    copyPasteTest("implementation 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", null, null));
  }

  public void test_AddProvided() {
    copyPasteTest("compileOnly 'group:artifact:1.0'", getDependency("group", "artifact", "1.0", "provided", null));
  }

  private void configureGradleFile(boolean isKotlinDsl) {
    configureFromFileText("build.gradle" + (isKotlinDsl ? ".kts" : ""),
                          """
                            dependencies {
                                <caret>
                            }""");
  }

  private void selectWholeFile() {
    Document document = getEditor().getDocument();
    getEditor().getSelectionModel().setSelection(0, document.getTextLength());
  }

  private void performCut() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_CUT);
    actionHandler.execute(getEditor(), null, getContext());
  }

  private void performPaste() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
    actionHandler.execute(getEditor(), null, getContext());
  }

  @Nullable
  private static DataContext getContext() {
    try {
      return DataManager.getInstance().getDataContextFromFocusAsync().blockingGet(100, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      ExceptionUtil.rethrow(e);
    }
    return null;
  }

  @NotNull
  private static String getDependency(@Nullable String groupId,
                                      @Nullable String artifactId,
                                      @Nullable String version,
                                      @Nullable String scope,
                                      @Nullable String classifier) {

    String dependency = "<dependency>\n";
    if (groupId != null) {
      dependency += "<groupId>" + groupId + "</groupId>\n";
    }
    if (artifactId != null) {
      dependency += "<artifactId>" + artifactId + "</artifactId>\n";
    }
    if (version != null) {
      dependency += "<version>" + version + "</version>\n";
    }
    if (scope != null) {
      dependency += "<scope>" + scope + "</scope>\n";
    }
    if (classifier != null) {
      dependency += "<classifier>" + classifier + "</classifier>\n";
    }
    dependency += "</dependency>";
    return dependency;
  }

  private void copyPasteTest(@NotNull String gradleDependency, @NotNull String mavenDependency) {
    copyPasteTest(gradleDependency, mavenDependency, false);
  }

  private void copyPasteTestKotlinDsl(@NotNull String gradleDependency, @NotNull String mavenDependency) {
    copyPasteTest(gradleDependency, mavenDependency, true);
  }

  private void copyPasteTest(@NotNull String gradleDependency, @NotNull String mavenDependency, boolean isKotlinDsl) {
    configureFromFileText("pom.xml", mavenDependency);
    selectWholeFile();
    performCut();

    configureGradleFile(isKotlinDsl);
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    " + gradleDependency + "\n" +
                      "}");
  }

  private static void directTransformationTest(@NotNull String gradleDependency, @NotNull String mavenDependency) {
    assertEquals(gradleDependency, toGradleDependency(mavenDependency, false));
  }

  private static void directTransformationTestKotlinDsl(@NotNull String gradleDependency, @NotNull String mavenDependency) {
    assertEquals(gradleDependency, toGradleDependency(mavenDependency, true));
  }
}

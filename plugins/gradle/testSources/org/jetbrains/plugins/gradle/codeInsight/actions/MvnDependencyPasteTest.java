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
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

import static org.jetbrains.plugins.gradle.action.PasteMvnDependencyPreProcessor.toGradleDependency;

public class MvnDependencyPasteTest extends LightJavaCodeInsightTestCase {

  public void testGradleDependencyScope() {
    directTransformationTest("compile 'group:artifact:1.0'", "2.14", getDependency("group", "artifact", "1.0", null, null));
    directTransformationTest("compile 'group:artifact:1.0'", "3.3", getDependency("group", "artifact", "1.0", null, null));
    directTransformationTest("compile 'group:artifact:1.0'", "3.3-rc-5", getDependency("group", "artifact", "1.0", null, null));
    directTransformationTest("implementation 'group:artifact:1.0'", "3.4", getDependency("group", "artifact", "1.0", null, null));
    directTransformationTest("implementation 'group:artifact:1.0'", "3.4-rc-1", getDependency("group", "artifact", "1.0", null, null));
    directTransformationTest("implementation 'group:artifact:1.0'", "6.7", getDependency("group", "artifact", "1.0", null, null));

    directTransformationTest("compile 'group:artifact:1.0'", "3.3", getDependency("group", "artifact", "1.0", "compile", null));
    directTransformationTest("implementation 'group:artifact:1.0'", "3.4", getDependency("group", "artifact", "1.0", "compile", null));

    directTransformationTest("testCompile 'group:artifact:1.0'", "3.3", getDependency("group", "artifact", "1.0", "test", null));
    directTransformationTest("testImplementation 'group:artifact:1.0'", "3.4", getDependency("group", "artifact", "1.0", "test", null));

    directTransformationTest("runtime 'group:artifact:1.0'", "3.3", getDependency("group", "artifact", "1.0", "runtime", null));
    directTransformationTest("runtime 'group:artifact:1.0'", "3.4", getDependency("group", "artifact", "1.0", "runtime", null));

    directTransformationTest("compileOnly 'group:artifact:1.0'", "3.3", getDependency("group", "artifact", "1.0", "provided", null));
    directTransformationTest("compileOnly 'group:artifact:1.0'", "3.4", getDependency("group", "artifact", "1.0", "provided", null));
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

  public void testDependencyWithClassifier() {
    copyPasteTest("runtime 'group:artifact:1.0:jdk14'", getDependency("group", "artifact", "1.0", "runtime", "jdk14"));
  }

  public void test_DoNotConvertIfCoordinatesNotClear() {
    String noArtifact = getDependency("group", null, "1.0", "runtime", null);
    configureFromFileText("pom.xml", noArtifact);
    selectWholeFile();
    performCut();
    configureGradleFile();
    int old = CodeInsightSettings.getInstance().REFORMAT_ON_PASTE;
    try {
      CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;
      performPaste();
      checkResultByText(null, "dependencies {\n" +
                              "    <dependency>\n" +
                              "<groupId>group</groupId>\n" +
                              "<version>1.0</version>\n" +
                              "<scope>runtime</scope>\n" +
                              "</dependency>\n" +
                              "}", true);
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

  private void configureGradleFile() {
    configureFromFileText("build.gradle",
                          "dependencies {\n" +
                          "    <caret>\n" +
                          "}");
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
    configureFromFileText("pom.xml", mavenDependency);
    selectWholeFile();
    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    " + gradleDependency + "\n" +
                      "}");
  }

  private static void directTransformationTest(@NotNull String gradleDependency,
                                               @NotNull String gradleVersion,
                                               @NotNull String mavenDependency) {
    String actualGradleDependency = toGradleDependency(mavenDependency, GradleVersion.version(gradleVersion));
    assertEquals(gradleDependency, actualGradleDependency);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.codeInsight.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MvnDependencyPasteTest extends LightJavaCodeInsightTestCase {

  public void testPastedGradleDependency() {
    configureFromFileText("pom.xml", getDependency("group", "artifact", "1.0", "runtime", null));
    selectWholeFile();
    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    runtime 'group:artifact:1.0'\n" +
                      "}");
  }

  public void testDependencyWithClassifier() {
    configureFromFileText("pom.xml", getDependency("group", "artifact", "1.0", "runtime", "jdk14"));
    selectWholeFile();
    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    runtime 'group:artifact:1.0:jdk14'\n" +
                      "}");
  }

  public void test_DoNotConvertIfCoordinatesNotClear() {
    String noArtifact = getDependency("group", null, "1.0", "runtime", null);
    configureFromFileText("pom.xml", noArtifact);

    selectWholeFile();

    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText(null, "dependencies {\n" +
                            "    <dependency>\n" +
                            "    <groupId>group</groupId>\n" +
                            "    <version>1.0</version>\n" +
                            "    <scope>runtime</scope>\n" +
                            "    </dependency>\n" +
                            "}", true);
  }

  public void test_AddCompile() {
    String noArtifact = getDependency("group", "artifact", "1.0", null, null);
    configureFromFileText("pom.xml", noArtifact);
    selectWholeFile();
    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    compile 'group:artifact:1.0'\n" +
                      "}");
  }

  public void test_AddProvided() {
    configureFromFileText("pom.xml", getDependency("group", "artifact", "1.0", "provided", null));
    selectWholeFile();
    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    compileOnly 'group:artifact:1.0'\n" +
                      "}");
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
    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
  }

  private void performPaste() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
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
}

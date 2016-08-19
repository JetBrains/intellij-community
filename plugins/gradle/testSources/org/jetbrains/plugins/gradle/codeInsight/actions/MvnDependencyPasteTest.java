/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.codeInsight.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MvnDependencyPasteTest extends LightCodeInsightTestCase {

  public void testPastedGradleDependency() throws Exception {
    configureFromFileText("pom.xml", getDependency("group", "artifact", "1.0", "runtime", null));
    selectWholeFile();
    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    runtime 'group:artifact:1.0'\n" +
                      "}");
  }
  
  public void test_DoNotConvertIfCoordinatesNotClear() throws Exception {
    String noArtifact = getDependency("group", null, "1.0", "runtime", null);
    configureFromFileText("pom.xml", noArtifact);

    selectWholeFile();
    
    performCut();

    configureGradleFile();
    performPaste();
    checkResultByText("dependencies {\n" +
                      "    <dependency>\n" +
                      "    <groupId>group</groupId>\n" +
                      "  <version>1.0</version>\n" +
                      "    <scope>runtime</scope>\n" +
                      "</dependency>\n" +
                      "}");
  }
  

  @NotNull
  private static String getDependency(@Nullable String groupId,
                                      @Nullable String artifactId,
                                      @Nullable String version,
                                      @Nullable String scope,
                                      @Nullable String type) {
    
    String dependency = "<dependency>\n";
    if (groupId != null) {
      dependency +=     "  <groupId>" + groupId + "</groupId>\n";
    }
    if (artifactId != null) {
      dependency +=     "  <artifactId>" + artifactId + "</artifactId>\n";
    }
    if (version != null) {
      dependency +=     "  <version>" + version + "</version>\n";     
    }
    if (scope != null) {
      dependency +=     "  <scope>" + scope + "</scope>\n";
    }
    if (type != null) {
      dependency +=     "  <type>" + type + "</type>\n";
    }
    dependency += "</dependency>";
    return dependency;
  }

  private static void configureGradleFile() {
    configureFromFileText("build.gradle",
                          "dependencies {\n" +
                          "    <caret>\n" +
                          "}");
  }

  private static void selectWholeFile() {
    Document document = getEditor().getDocument();
    getEditor().getSelectionModel().setSelection(0, document.getTextLength());
  }

  private static void performCut() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_CUT);
    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
  }

  private static void performPaste() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContextFromFocus().getResultSync());
  }
}

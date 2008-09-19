/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.optimizeImports;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author ilyas
 */
public class OptimizeImportsTest extends IdeaTestCase {

  protected Sdk getTestProjectJdk() {
    return JavaSdk.getInstance().createJdk("java sdk", TestUtils.getMockJdkHome(), false);
  }

  protected void setUp() throws Exception {
    super.setUp();
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(TestUtils.getTestDataPath() + "/optimizeImports");
    assertNotNull(root);
    final VirtualFile testRoot = root.findChild(getTestName(true));
    assertNotNull(testRoot);
    ContentEntry contentEntry = rootModel.addContentEntry(testRoot);
    contentEntry.addSourceFolder(testRoot, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }

  public void testNewline() throws Throwable {
    doTest("newline", "A.groovy");
  }

  public void testAliased() throws Throwable {
    doTest("aliased", "A.groovy");
  }

  public void testSimpleOptimize() throws Throwable {
    doTest("simpleOptimize", "A.groovy");
  }

  public void testCommented() throws Throwable {
    doTest("commented", "A.groovy");
  }

  public void testOptimizeExists() throws Throwable {
    doTest("optimizeExists", "A.groovy");
  }

  public void testOptimizeAlias() throws Throwable {
    doTest("optimizeAlias", "A.groovy");
  }

  public void testFoldImports() throws Throwable {
    doTest("foldImports", "A.groovy");
  }

  public void testFoldImports2() throws Throwable {
    doTest("foldImports2", "A.groovy");
  }

  public void testUntypedCall() throws Throwable {
    doTest("untypedCall", "A.groovy");
  }

  public void testFoldImports3() throws Throwable {
    doTest("foldImports3", "A.groovy");
  }

  public void testFoldImports4() throws Throwable {
    doTest("foldImports4", "A.groovy");
  }

  public void testFoldImports5() throws Throwable {
    doTest("foldImports5", "A.groovy");
  }

  public void testFixPoint() throws Throwable {
    doTest("fixPoint", "A.groovy");
  }

  public void testSemicolons() throws Throwable {
    doTest("semicolons", "A.groovy");
  }

  private void doTest(String folder, String filePath) throws Throwable {
    setImportSettings();
    String basePath = TestUtils.getTestDataPath() + "/optimizeImports/";
    String resultText = getResultFromFile(basePath + folder);
    VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + basePath + folder + "/" + filePath);

    assertNotNull("Virtual file points to null", virtualFile);

    PsiManager manager = PsiManager.getInstance(myProject);
    PsiFile file = manager.findFile(virtualFile);

    assertNotNull("PsiFile points to null", file);

    GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
    final Runnable runnable = optimizer.processFile(file);

    CommandProcessor.getInstance().executeCommand(
        myProject,
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(runnable);
          }
        }, "Optimize imports", null);

    String text = file.getText();
    //System.out.println("---------------------------- " + folder + " ----------------------------");
    //System.out.println(text);
    assertEquals("Results are not equal", resultText, text);

  }

  private void setImportSettings() {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
    settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
    settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
  }

  private String getResultFromFile(String basePath) throws IOException {
    StringBuffer contents = new StringBuffer();
    String line;
    File aFile = new File(basePath + "/" + "result.test");
    BufferedReader input = new BufferedReader(new FileReader(aFile));
    while ((line = input.readLine()) != null) {
      if (contents.length() != 0) {
        contents.append("\n");
      }
      contents.append(line);
    }
    return contents.toString();
  }

}

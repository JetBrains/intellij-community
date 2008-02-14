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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;

/**
 * @author ilyas
 */
public class ExtractMethodTest extends ActionTestCase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/refactoring/extractMethod/data/";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;


  public ExtractMethodTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result = "";
    String fileText = file.getText();
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    fileText = TestUtils.removeBeginMarker(fileText);
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
    fileEditorManager = FileEditorManager.getInstance(myProject);
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, myFile.getVirtualFile(), 0), false);

    Assert.assertTrue(myFile instanceof GroovyFile);
    try {
      myEditor.getSelectionModel().setSelection(startOffset, endOffset);
      GroovyExtractMethodHandler handler = new GroovyExtractMethodHandler();
      boolean invoked = handler.invokeOnEditor(myProject, myEditor, myFile);
      result = invoked ? myEditor.getDocument().getText() : "FAILED: " + handler.getInvokeResult() ;
      int caretOffset = myEditor.getCaretModel().getOffset();
      result = invoked ? result.substring(0, caretOffset) + TestUtils.CARET_MARKER + result.substring(caretOffset) : result;
    } finally {
      fileEditorManager.closeFile(myFile.getVirtualFile());
      myEditor = null;
    }

    return result;
  }


  public String transform(String testName, String[] data) throws Exception {
    setSettings();
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
    String result = processFile(psiFile);
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(result);
    System.out.println("");
    return result;
  }


  public static Test suite() {
    return new ExtractMethodTest();
  }

  protected IdeaProjectTestFixture createFixture() {
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createFixtureBuilder();
    JavaModuleFixtureBuilder fixtureBuilder = builder.addModule(JavaModuleFixtureBuilder.class);
    fixtureBuilder.addJdk(TestUtils.getMockJdkHome());
    fixtureBuilder.addLibraryJars("GROOVY", TestUtils.getMockGrailsLibraryHome(), TestUtils.GROOVY_JAR);
    return builder.getFixture();
  }

  protected void setUp() {
    super.setUp();
  }
}
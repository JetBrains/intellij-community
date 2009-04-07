package org.jetbrains.plugins.groovy.lang.gotosuper;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.PathUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GroovyGoToSuperTest extends ActionTestCase {

  @NonNls
  private static final String DATA_PATH = PathUtil.getDataPath(GroovyGoToSuperTest.class);

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public GroovyGoToSuperTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
  }


  private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
    String result;
    String fileText = file.getText();
    int offset = fileText.indexOf(CARET_MARKER);
    fileText = TestUtils.removeCaretMarker(fileText);
    myFile = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
    fileEditorManager = FileEditorManager.getInstance(myProject);
    VirtualFile virtualFile = myFile.getVirtualFile();
    assert virtualFile != null;
    myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, virtualFile, 0), false);
    Assert.assertNotNull(myEditor);
    myEditor.getCaretModel().moveToOffset(offset);

    final myDataContext dataContext = getDataContext(myFile);
    final CodeInsightActionHandler handler = CodeInsightActions.GOTO_SUPER.forLanguage(GroovyFileType.GROOVY_LANGUAGE);

    try {
      performAction(myProject, new Runnable() {
        public void run() {
          handler.invoke(myProject,  myEditor, myFile);
        }
      });

      offset = myEditor.getCaretModel().getOffset();
      result = myEditor.getDocument().getText();
      result = result.substring(0, offset) + CARET_MARKER + result.substring(offset);
    } finally {
      fileEditorManager.closeFile(virtualFile);
      myEditor = null;
    }

    return result;
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(myProject, fileText);
    String result = processFile(psiFile);
    //System.out.println("------------------------ " + testName + " ------------------------");
    //System.out.println(result);
    //System.out.println("");
    return result;
  }


  public static Test suite() {
    return new GroovyGoToSuperTest();
  }
}
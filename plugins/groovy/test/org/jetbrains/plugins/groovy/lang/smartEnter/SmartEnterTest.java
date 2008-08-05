package org.jetbrains.plugins.groovy.lang.smartEnter;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.lang.Language;
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
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 05.08.2008
 */
public class SmartEnterTest extends ActionTestCase {

  @NonNls
  private static final String DATA_PATH = "./test/org/jetbrains/plugins/groovy/lang/smartEnter/data/";

  protected Editor myEditor;
  protected FileEditorManager fileEditorManager;
  protected String newDocumentText;
  protected PsiFile myFile;

  public SmartEnterTest() {
    super(System.getProperty("path") != null ?
            System.getProperty("path") :
            DATA_PATH
    );
  }

  protected List<SmartEnterProcessor> getSmartProcessors(Language grLanguage) {
    return SmartEnterProcessors.INSTANCE.forKey(grLanguage);
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

    final List<SmartEnterProcessor> processors = getSmartProcessors(myFile.getLanguage());

    try {
      performAction(myProject, new Runnable() {
        public void run() {
          for (SmartEnterProcessor processor : processors) {
            processor.process(myProject, myEditor, myFile);
          }
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
    return new SmartEnterTest();
  }
}

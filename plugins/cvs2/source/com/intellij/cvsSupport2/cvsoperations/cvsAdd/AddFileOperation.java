package com.intellij.cvsSupport2.cvsoperations.cvsAdd;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.command.add.AddCommand;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;

import java.util.Iterator;
import java.util.List;

public class AddFileOperation extends CvsOperationOnFiles {
  private final KeywordSubstitution myKeywordSubstitution;

  public AddFileOperation(KeywordSubstitution keywordSubstitution) {
    myKeywordSubstitution = keywordSubstitution;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    AddCommand result = new AddCommand();
    result.setKeywordSubst(myKeywordSubstitution);
    addFilesToCommand(root, result);
    return result;
  }

  protected void addFilesToCommand(CvsRootProvider root, AbstractCommand command) {
    super.addFilesToCommand(root, command);
    List fileObjects = command.getFileObjects().getFileObjects();
    for (Iterator each = fileObjects.iterator(); each.hasNext();) {
      AbstractFileObject fileObject = (AbstractFileObject)each.next();
      if (fileObject.getParent() == null){
        LOG.assertTrue(false, "Local Root: " + getLocalRootFor(root) + ", Files: " + myFiles);
      }
    }
  }

  protected String getOperationName() {
    return "add";
  }
}

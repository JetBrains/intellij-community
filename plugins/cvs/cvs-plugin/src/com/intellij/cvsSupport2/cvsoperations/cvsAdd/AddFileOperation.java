package com.intellij.cvsSupport2.cvsoperations.cvsAdd;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;
import org.netbeans.lib.cvsclient.command.add.AddCommand;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;

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
    List<AbstractFileObject> fileObjects = command.getFileObjects().getFileObjects();
    for (final AbstractFileObject fileObject: fileObjects) {
      if (fileObject.getParent() == null) {
        LOG.assertTrue(false, "Local Root: " + getLocalRootFor(root) + ", Files: " + myFiles);
      }
    }
  }

  protected String getOperationName() {
    return "add";
  }

  @Override
  public boolean runInReadThread() {
    return false;
  }
}

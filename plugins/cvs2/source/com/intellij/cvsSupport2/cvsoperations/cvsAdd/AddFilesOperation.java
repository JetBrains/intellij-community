package com.intellij.cvsSupport2.cvsoperations.cvsAdd;

import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperaton;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * author: lesya
 */
public class AddFilesOperation extends CompositeOperaton {

  private final Map<KeywordSubstitution, AddFileOperation> mySubstitutionToOperation = new HashMap<KeywordSubstitution, AddFileOperation>();
  private final Map<VirtualFile, AddFileOperation> myAlreadyProcessedParentToOperation = new HashMap<VirtualFile, AddFileOperation>();
  private int myFilesCount = -1;


  public AddFilesOperation() {
  }

  public boolean login(ModalityContext executor) throws CannotFindCvsRootException {
    for (Iterator iterator = mySubstitutionToOperation.keySet().iterator(); iterator.hasNext();) {
      KeywordSubstitution keywordSubstitution = (KeywordSubstitution)iterator.next();
      addOperation(mySubstitutionToOperation.get(keywordSubstitution));
    }
    return super.login(executor);
  }

  public void addFile(VirtualFile file, KeywordSubstitution keywordSubstitution) {
    if (file.isDirectory()) {

      AddFileOperation op = getOperationForFile(file);
      op.addFile(file);
      if (!containsSubOperation(op)) {
        addOperation(op);
      }

    }
    else {
      if (!mySubstitutionToOperation.containsKey(keywordSubstitution)) {
        mySubstitutionToOperation.put(keywordSubstitution, new AddFileOperation(keywordSubstitution));
      }
      mySubstitutionToOperation.get(keywordSubstitution).addFile(file);
    }
  }

  private AddFileOperation getOperationForFile(VirtualFile file) {
    VirtualFile parent = file.getParent();
    if (parent == null) {
      return new AddFileOperation(null);
    }
    if (!myAlreadyProcessedParentToOperation.containsKey(parent)) {
      myAlreadyProcessedParentToOperation.put(parent, new AddFileOperation(null));
    }
    return myAlreadyProcessedParentToOperation.get(parent);
  }

  public int getFilesToProcessCount() {
    if (myFilesCount == -1){
      myFilesCount = calculateAllFilesCount();
    }
    return myFilesCount;
  }

  private int calculateAllFilesCount() {
    int result = 0;
    for (Iterator iterator = getSubOperations().iterator(); iterator.hasNext();) {
      AddFileOperation addFileOprtation = (AddFileOperation)iterator.next();
      result += addFileOprtation.getFilesCount();
    }
    return result;
  }
}

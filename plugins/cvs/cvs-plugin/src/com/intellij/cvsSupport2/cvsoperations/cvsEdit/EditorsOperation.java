package com.intellij.cvsSupport2.cvsoperations.cvsEdit;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.reservedcheckout.EditorsCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class EditorsOperation extends CvsOperationOnFiles{
  private final List<EditorInfo> myEditorInfos = new ArrayList<EditorInfo>();

  public EditorsOperation(VirtualFile[] files){
    for (int i = 0; i < files.length; i++) {
      addFile(files[i]);
    }

  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    EditorsCommand result = new EditorsCommand();
    addFilesToCommand(root, result);
    return result;
  }

  public void messageSent(String message, final byte[] byteMessage, boolean error, boolean tagged) {
    super.messageSent(message, byteMessage, error, tagged);
    if (!error && !tagged){
      EditorInfo info = EditorInfo.createOn(message);
      if (info != null) myEditorInfos.add(info);
    }
  }

  protected String getOperationName() {
    return "editors";
  }

  public List<EditorInfo> getEditors(){
    return myEditorInfos;
  }
}

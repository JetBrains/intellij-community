package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperationOnFiles;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.tag.TagCommand;

/**
 * author: lesya
 */
public class TagOperation extends CvsOperationOnFiles{
  private final String myTag;
  private final boolean myRemoveTag;
  private final boolean myOverrideExisting;

  public TagOperation(VirtualFile[] files, String tag, boolean removeTag, boolean overrideExisting) {
    for (VirtualFile file : files) {
      addFile(file);
    }
    myRemoveTag = removeTag;
    myTag = tag;
    myOverrideExisting = overrideExisting;
  }

  public TagOperation(FilePath[] files, String tag, boolean overrideExisting) {
    for (FilePath file : files) {
      addFile(file.getIOFile());
    }
    myRemoveTag = false;
    myTag = tag;
    myOverrideExisting = overrideExisting;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    TagCommand tagCommand = new TagCommand();
    addFilesToCommand(root, tagCommand);
    tagCommand.setTag(myTag);
    tagCommand.setDeleteTag(myRemoveTag);
    tagCommand.setOverrideExistingTag(myOverrideExisting);
    return tagCommand;
  }

  protected String getOperationName() {
    return "tag";
  }
}

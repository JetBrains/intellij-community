package com.intellij.cvsSupport2.actions.update;

import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

/**
 * author: lesya
 */
public class UpdateByBranchUpdateSettings implements UpdateSettings{
  private final String myBranchName;
  private final boolean myMakeNewFilesReadOnly;

  public UpdateByBranchUpdateSettings(String branchName, boolean makeNewFilesReadOnly) {
    myBranchName = branchName;
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
  }

  public boolean getPruneEmptyDirectories() {
    return false;
  }

  public String getBranch1ToMergeWith() {
    return null;
  }

  public String getBranch2ToMergeWith() {
    return null;
  }

  public boolean getResetAllSticky() {
    return false;
  }

  public boolean getDontMakeAnyChanges() {
    return false;
  }

  public boolean getCreateDirectories() {
    return true;
  }

  public boolean getCleanCopy() {
    return false;
  }

  public KeywordSubstitution getKeywordSubstitution() {
    return null;
  }

  public RevisionOrDate getRevisionOrDate() {
    return new SimpleRevision(myBranchName);
  }

  public boolean getMakeNewFilesReadOnly() {
    return myMakeNewFilesReadOnly;
  }
}

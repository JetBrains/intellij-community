package com.intellij.cvsSupport2.actions.update;

import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

/**
 * author: lesya
 */
public interface UpdateSettings {
  boolean getPruneEmptyDirectories();
  String getBranch1ToMergeWith();
  String getBranch2ToMergeWith();
  boolean getResetAllSticky();
  boolean getDontMakeAnyChanges();
  boolean getCreateDirectories();
  boolean getCleanCopy();
  RevisionOrDate getRevisionOrDate();
  KeywordSubstitution getKeywordSubstitution();
  boolean getMakeNewFilesReadOnly();

  UpdateSettings DONT_MAKE_ANY_CHANGES = new UpdateSettings(){
    public boolean getPruneEmptyDirectories() {
      return false;
    }

    public String getBranch1ToMergeWith() {
      return null;
    }

    public boolean getResetAllSticky() {
      return false;
    }

    public boolean getDontMakeAnyChanges() {
      return true;
    }

    public String getBranch2ToMergeWith() {
      return null;
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

    public boolean getMakeNewFilesReadOnly() {
      return false;
    }

    public RevisionOrDate getRevisionOrDate() {
      return RevisionOrDate.EMPTY;
    }
  };
}

package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.cvsoperations.cvsLog.LogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LocalPathIndifferentLogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LocalPathIndifferentLogOperation;
import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProvider;
import com.intellij.openapi.vcs.FilePath;

import java.util.Collection;

/**
 * author: lesya
 */
public class TagsProviderOnVirtualFiles implements TagsProvider {
  private final Collection<FilePath> myFiles;

  public TagsProviderOnVirtualFiles(Collection<FilePath> files) {
    myFiles = files;
  }

  public CvsCommandOperation getOperation() {
    boolean containsOneFile = containsOneFile();
    if (containsOneFile) {
      return new LocalPathIndifferentLogOperation(getFirstFile().getIOFile());
    }
    else {
      return new LogOperation(myFiles);
    }
  }

  private boolean containsOneFile() {
    if (myFiles.size() != 1) return false;
    return !getFirstFile().isDirectory();
  }

  private FilePath getFirstFile() {
    return myFiles.iterator().next();
  }
}

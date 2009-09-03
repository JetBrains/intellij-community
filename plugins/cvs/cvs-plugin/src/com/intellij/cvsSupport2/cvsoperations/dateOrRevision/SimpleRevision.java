package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;


/**
 * author: lesya
 */
public class SimpleRevision implements RevisionOrDate {
  private final String myRevision;

  public static SimpleRevision createForTheSameVersionOf(@NotNull VirtualFile file) {
    Entry entry = CvsEntriesManager.getInstance().getEntryFor(file.getParent(), file.getName());
    return new SimpleRevision(entry.getRevision());

  }

  public SimpleRevision(String revision) {
    myRevision = prepareRevision(revision);
  }

  private String prepareRevision(String revision) {
    if (revision == null) {
      return null;
    }
    else if (StringUtil.startsWithChar(revision, '-')) {
      return revision.substring(1);
    }
    else {
      return revision;
    }
  }

  public String getRevision() {
    return myRevision;
  }

  public void setForCommand(Command command) {
    new CommandWrapper(command).setUpdateByRevisionOrDate(myRevision, null);
  }

  public CvsRevisionNumber getCvsRevisionNumber() {
    if (myRevision == null) return null;
    try {
      return new CvsRevisionNumber(myRevision);
    }
    catch (NumberFormatException ex) {
      return null;
    }
  }

}

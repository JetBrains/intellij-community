package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;

/**
 * author: lesya
 */
public class RevisionOrDateImpl implements RevisionOrDate {
  private String myStickyTag;
  private String myStickyDate;

  public static RevisionOrDate createOn(VirtualFile file) {
    VirtualFile parent = CvsVfsUtil.getParentFor(file);
    return new RevisionOrDateImpl(parent, CvsEntriesManager.getInstance().getEntryFor(parent, file.getName()));
  }

  public static RevisionOrDate createOn(VirtualFile parent, String name) {
    return new RevisionOrDateImpl(parent, CvsEntriesManager.getInstance().getEntryFor(parent, name));
  }

  public static RevisionOrDate createOn(VirtualFile parent, Entry entry, DateOrRevisionSettings config) {
    RevisionOrDateImpl result = new RevisionOrDateImpl(parent, entry);
    updateOn(result, config);
    return result;
  }

  private static void updateOn(RevisionOrDateImpl result, DateOrRevisionSettings config) {
    String stickyTagFromConfig = config.USE_BRANCH ? config.BRANCH : null;
    String stickyDateFromConfig = config.USE_DATE ? config.getDate() : null;
    result.setStickyInfo(stickyTagFromConfig, stickyDateFromConfig);
  }

  public static RevisionOrDate createOn(DateOrRevisionSettings config) {
    RevisionOrDateImpl result = new RevisionOrDateImpl();
    updateOn(result, config);
    return result;
  }

  private RevisionOrDateImpl() {

  }

  private RevisionOrDateImpl(VirtualFile parent, Entry entry) {
    if (entry == null) {
      lookupDirectory(parent);
    }
    else {
      if (entry.getStickyRevision() != null) {
        myStickyTag = entry.getStickyRevision();
      }
      else if (entry.getStickyTag() != null) {
        myStickyTag = entry.getStickyTag();
      }
      else if (entry.getStickyDateString() != null) {
        myStickyDate = entry.getStickyDateString();
      }
      else {
        lookupDirectory(parent);
      }
    }
  }

  private void setStickyInfo(String stickyTag, String stickyDate) {
    if ((stickyTag == null) && (stickyDate == null)) return;
    if (stickyTag != null) {
      myStickyDate = null;
      myStickyTag = stickyTag;
    }
    else {
      myStickyTag = null;
      myStickyDate = stickyDate;
    }
  }

  public void setForCommand(Command command) {
    CommandWrapper wrapper = new CommandWrapper(command);
    wrapper.setUpdateByRevisionOrDate(myStickyTag, myStickyDate);
  }

  private void lookupDirectory(VirtualFile directory) {
    String stickyTag = CvsUtil.getStickyTagForDirectory(directory);
    if (stickyTag != null) {
      myStickyTag = stickyTag;
      return;
    }
    myStickyDate = CvsUtil.getStickyDateForDirectory(directory);
  }

  public String getRevision() {
    return myStickyTag;
  }

  public CvsRevisionNumber getCvsRevisionNumber() {
    if (myStickyTag == null) return null;
    try {
      return new CvsRevisionNumber(myStickyTag);
    }
    catch (NumberFormatException ex) {
      return null;
    }
  }

  public String toString() {
    if (myStickyDate != null) {
      return myStickyDate;
    } else {
      return myStickyTag;
    }
  }
}

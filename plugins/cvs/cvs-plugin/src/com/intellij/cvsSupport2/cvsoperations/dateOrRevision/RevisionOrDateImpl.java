/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsoperations.dateOrRevision;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.DateOrRevisionSettings;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.Command;

import java.text.ParseException;
import java.util.Date;

/**
 * @author lesya
 */
public class RevisionOrDateImpl implements RevisionOrDate {

  private static final Logger LOG = Logger.getInstance(RevisionOrDateImpl.class);

  @Nullable private String myStickyTag;
  @Nullable private Date myStickyDate;

  public static RevisionOrDate createOn(@NotNull VirtualFile file) {
    final VirtualFile parent = file.getParent();
    return new RevisionOrDateImpl(parent, CvsEntriesManager.getInstance().getEntryFor(parent, file.getName()));
  }

  public static RevisionOrDate createOn(VirtualFile parent, String name) {
    return new RevisionOrDateImpl(parent, CvsEntriesManager.getInstance().getEntryFor(parent, name));
  }

  public static RevisionOrDate createOn(VirtualFile parent, Entry entry, DateOrRevisionSettings config) {
    final RevisionOrDateImpl result = new RevisionOrDateImpl(parent, entry);
    updateOn(result, config);
    return result;
  }

  private static void updateOn(RevisionOrDateImpl result, DateOrRevisionSettings config) {
    final String stickyTagFromConfig = config.USE_BRANCH ? config.BRANCH : null;
    final String stickyDateFromConfig = config.USE_DATE ? config.getDate() : null;
    result.setStickyInfo(stickyTagFromConfig, stickyDateFromConfig);
  }

  @NotNull
  public static RevisionOrDate createOn(DateOrRevisionSettings config) {
    final RevisionOrDateImpl result = new RevisionOrDateImpl();
    updateOn(result, config);
    return result;
  }

  private RevisionOrDateImpl() {}

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
        myStickyDate = entry.getStickyDate();
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
      try {
        myStickyDate = Entry.getLastModifiedDateFormatter().parse(stickyDate);
      }
      catch (ParseException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void setForCommand(Command command) {
    command.setUpdateByRevisionOrDate(myStickyTag, myStickyDate == null ? null : Entry.getLastModifiedDateFormatter().format(myStickyDate));
  }

  private void lookupDirectory(VirtualFile directory) {
    final String stickyTag = CvsUtil.getStickyTagForDirectory(directory);
    if (stickyTag != null) {
      myStickyTag = stickyTag;
      return;
    }
    try {
      final String stickyDateString = CvsUtil.getStickyDateForDirectory(directory);
      if (stickyDateString != null) {
        myStickyDate = Entry.STICKY_DATE_FORMAT.parse(stickyDateString);
      }
    }
    catch (ParseException e) {
      LOG.error(e);
    }
  }

  @Override
  public String getRevision() {
    if (myStickyTag == null) {
      return "HEAD";
    }
    return myStickyTag;
  }

  @Override
  public CvsRevisionNumber getCvsRevisionNumber() {
    if (myStickyTag == null) return null;
    try {
      return new CvsRevisionNumber(myStickyTag);
    }
    catch (NumberFormatException e) {
      LOG.error(e);
      return null;
    }
  }

  public String toString() {
    if (myStickyDate != null) {
      return Entry.getLastModifiedDateFormatter().format(myStickyDate);
    } else {
      return myStickyTag;
    }
  }
}

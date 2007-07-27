/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2.updateinfo;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvshandlers.CvsUpdatePolicy;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.FileMessage;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;

public class UpdatedFilesProcessor  extends CvsMessagesAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.updateinfo.UpdatedFilesProcessor");

  private Project myProject;
  private final UpdatedFiles myUpdatedFiles;

  public UpdatedFilesProcessor(Project project, UpdatedFiles updatedFiles) {
    myProject = project;
    myUpdatedFiles = updatedFiles;
  }

  public void addFileMessage(FileMessage message) {
    String path = message.getFileAbsolutePath();
    VirtualFile virtualFile = getVirtualFileFor(path);
    FileGroup collection = getCollectionFor(message.getType(), virtualFile);
    LOG.assertTrue(collection != null, String.valueOf(message.getType()));
    final CvsRevisionNumber revision = message.getRevision();
    if (revision != null) {
      collection.add(path, CvsVcs2.getInstance(myProject), revision);
    }
    else {
      collection.add(path);
    }
  }

  private FileGroup getCollectionFor(int messageType, @Nullable VirtualFile vFile) {
    switch (messageType) {
      case FileMessage.MODIFIED:
        return myUpdatedFiles.getGroupById(FileGroup.MODIFIED_ID);
      case FileMessage.MERGED:
        return getMergedFileGroup(vFile, FileGroup.MERGED_ID);
      case FileMessage.MERGED_WITH_CONFLICTS:
        return getMergedFileGroup(vFile, FileGroup.MERGED_WITH_CONFLICT_ID);
      case FileMessage.CREATED_BY_SECOND_PARTY:
        return myUpdatedFiles.getGroupById(CvsUpdatePolicy.CREATED_BY_SECOND_PARTY_ID);
      case FileMessage.NOT_IN_REPOSITORY:
        return myUpdatedFiles.getGroupById(FileGroup.UNKNOWN_ID);
      case FileMessage.LOCALLY_ADDED:
        return myUpdatedFiles.getGroupById(FileGroup.LOCALLY_ADDED_ID);
      case FileMessage.LOCALLY_REMOVED:
        return myUpdatedFiles.getGroupById(FileGroup.LOCALLY_REMOVED_ID);
      case FileMessage.REMOVED_FROM_REPOSITORY:
        return myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID);
      case FileMessage.CREATED:
        {
          return myUpdatedFiles.getGroupById(FileGroup.CREATED_ID);
        }
      case FileMessage.UPDATING:
        {
          if (vFile == null) {
            return myUpdatedFiles.getGroupById(FileGroup.RESTORED_ID);
          }
          else {
            return myUpdatedFiles.getGroupById(FileGroup.UPDATED_ID);
          }
        }
      case FileMessage.PATCHED:
        return myUpdatedFiles.getGroupById(FileGroup.UPDATED_ID);
      case FileMessage.REMOVED_FROM_SERVER_CONFLICT:
        return myUpdatedFiles.getGroupById(CvsUpdatePolicy.MODIFIED_REMOVED_FROM_SERVER_ID);
      case FileMessage.LOCALLY_REMOVED_CONFLICT:
        return myUpdatedFiles.getGroupById(CvsUpdatePolicy.LOCALLY_REMOVED_MODIFIED_ON_SERVER_ID);

    }
    return myUpdatedFiles.getGroupById(FileGroup.UNKNOWN_ID);
  }

  private FileGroup getMergedFileGroup(final VirtualFile vFile, final String textMergedId) {
    if (vFile != null) {
      Entry entry = CvsEntriesManager.getInstance().getEntryFor(vFile);
      if (entry != null && entry.isBinary()) {
        return myUpdatedFiles.getGroupById(CvsUpdatePolicy.BINARY_MERGED_ID);
      }
    }
    return myUpdatedFiles.getGroupById(textMergedId);
  }


  public static VirtualFile getVirtualFileFor(final String path) {
    if (path == null) return null;
    return CvsVfsUtil.findFileByPath(path.replace(File.separatorChar, '/'));
  }
}

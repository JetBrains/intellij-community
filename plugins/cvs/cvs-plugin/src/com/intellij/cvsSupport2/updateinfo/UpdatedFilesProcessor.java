/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.updateinfo;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvshandlers.CvsUpdatePolicy;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.FileMessage;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;

public class UpdatedFilesProcessor  extends CvsMessagesAdapter {
  private static final Logger LOG = Logger.getInstance(UpdatedFilesProcessor.class);

  private final UpdatedFiles myUpdatedFiles;

  public UpdatedFilesProcessor(UpdatedFiles updatedFiles) {
    myUpdatedFiles = updatedFiles;
  }

  @Override
  public void addFileMessage(FileMessage message) {
    String path = message.getFileAbsolutePath();
    VirtualFile virtualFile = getVirtualFileFor(path);
    final int messageType = message.getType();
    if (virtualFile != null && messageType == FileMessage.NOT_IN_REPOSITORY &&
        FileTypeManager.getInstance().isFileIgnored(virtualFile)) {
      return;
    }
    FileGroup collection = getCollectionFor(messageType, virtualFile);
    LOG.assertTrue(collection != null, String.valueOf(messageType));
    final CvsRevisionNumber revision = message.getRevision();
    collection.add(path, CvsVcs2.getKey(), revision);
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

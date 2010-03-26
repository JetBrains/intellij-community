/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsUpdate;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.common.PostCvsActivity;
import com.intellij.cvsSupport2.cvsoperations.common.ReceivedFileProcessor;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.projectImport.ProjectOpenProcessor;

import java.io.File;
import java.io.IOException;

/**
 * author: lesya
 */
public class UpdateReceivedFileProcessor implements ReceivedFileProcessor {

  private final UpdatedFilesManager myUpdatedFilesInfo;

  private final PostCvsActivity myPostCvsActivity;

  public UpdateReceivedFileProcessor(UpdatedFilesManager updatedFilesInfo,
                                     PostCvsActivity postCvsActivity) {
    myUpdatedFilesInfo = updatedFilesInfo;
    myPostCvsActivity = postCvsActivity;
  }

  public boolean shouldProcess(VirtualFile virtualFile, File targetFile) throws IOException {
    if (isProjectOrModuleFile(virtualFile) && myUpdatedFilesInfo.isMerged(targetFile)) {
      myUpdatedFilesInfo.couldNotUpdateFile(targetFile);      
      File backupCopy = getCopyFor(virtualFile, targetFile);
      myPostCvsActivity.registerCorruptedProjectOrModuleFile(
        new MergedWithConflictProjectOrModuleFile(virtualFile));
      FileUtil.copy(backupCopy, targetFile);
      return false;
    }
    else {
      return true;
    }
  }

  private static File getCopyFor(VirtualFile virtualFile, File targetFile) {
    return new File(targetFile.getParentFile(),
                    ".#" + targetFile.getName() + "." + CvsEntriesManager.getInstance().getEntryFor(virtualFile).getRevision());
  }

  private static boolean isProjectOrModuleFile(VirtualFile virtualFile) {
    if (virtualFile == null) return false;
    if (ProjectOpenProcessor.getImportProvider(virtualFile) != null) return true;
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
    return
      fileType == StdFileTypes.IDEA_PROJECT
      || fileType == StdFileTypes.IDEA_MODULE
      || fileType == StdFileTypes.IDEA_WORKSPACE;
  }
}

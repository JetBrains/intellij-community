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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * author: lesya
 */
public abstract class AnyProcessedFiles extends FileSetToBeUpdated {
  private static final Logger LOG = Logger.getInstance(AnyProcessedFiles.class);

  public abstract Collection<VirtualFile> getFiles();


  @Override
  public void refreshFilesAsync(final Runnable postRunnable) {
    final VirtualFile[] files = VfsUtil.toVirtualFileArray(getFiles());
    final int[] index = new int[]{0};
    LOG.info("files.length=" + files.length);
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (index[0] < files.length){
          VirtualFile file = files[index[0]++];
          if (file.isValid()){
            LOG.info("Refreshing:" + file);
            file.refresh(true, true, this);
          }
          else{
            LOG.info("Skipping file");
            this.run();
          }
        }
        else{
          LOG.info("postRunnable!");
          if (postRunnable != null){
            postRunnable.run();
          }
        }
      }
    };
    runnable.run();

    /*
    if (project != null){
      FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
      for (Iterator each = getFiles().iterator(); each.hasNext();) {
        fileStatusManager.fileStatusChanged((VirtualFile)each.next());
      }
    }
    */
  }

  @Override
  public void refreshFilesSync() {
    for(VirtualFile file: getFiles()) {
      file.refresh(false, true);
    }
  }
}

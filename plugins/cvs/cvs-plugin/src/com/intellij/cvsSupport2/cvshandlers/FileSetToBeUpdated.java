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

import com.intellij.CvsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;


/**
 * author: lesya
 */
public abstract class FileSetToBeUpdated {

  private static final Logger LOG = Logger.getInstance(FileSetToBeUpdated.class);

  public static FileSetToBeUpdated allFiles() {
    return new AllFilesInProject();
  }

  public static FileSetToBeUpdated selectedFiles(FilePath[] files) {
    return new SelectedFiles(files);
  }

  public static FileSetToBeUpdated selectedFiles(VirtualFile[] files) {
    return new SelectedFiles(files);
  }

  public final static FileSetToBeUpdated EMPTY = new FileSetToBeUpdated() {
    @Override
    public void refreshFilesAsync(Runnable postRunnable) {
      if (postRunnable != null) {
        postRunnable.run();
      }
    }

    @Override
    public void refreshFilesSync() {
    }

    @Override
    protected void setSynchronizingFilesTextToProgress(ProgressIndicator progressIndicator) {

    }
  };

  public abstract void refreshFilesAsync(Runnable postRunnable);
  public abstract void refreshFilesSync();

  protected void setSynchronizingFilesTextToProgress(ProgressIndicator progressIndicator) {
    progressIndicator.setText(CvsBundle.message("progress.text.synchronizing.files"));
    progressIndicator.setText2("");
  }
}

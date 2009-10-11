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
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;

import java.util.Collection;

/**
 * @author yole
 */
public class VcsHandleType extends HandleType {
  private final AbstractVcs myVcs;

  public VcsHandleType(AbstractVcs vcs) {
    super(VcsBundle.message("handle.ro.file.status.type.using.vcs", vcs.getDisplayName()), true);
    myVcs = vcs;
  }

  public void processFiles(final Collection<VirtualFile> files) {
    try {
      myVcs.getEditFileProvider().editFiles(files.toArray(new VirtualFile[files.size()]));
    }
    catch (VcsException e) {
      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.edit.file", e.getLocalizedMessage()),
                               VcsBundle.message("message.title.edit.files"));
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final VirtualFile file : files) {
          file.refresh(false, false);
        }

      }
    });
  }
}
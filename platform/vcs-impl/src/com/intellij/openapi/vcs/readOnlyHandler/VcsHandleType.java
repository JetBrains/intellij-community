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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class VcsHandleType extends HandleType {
  private static final Function<LocalChangeList,String> FUNCTION = new Function<LocalChangeList, String>() {
    @Override
    public String fun(LocalChangeList list) {
      return list.getName();
    }
  };
  private final AbstractVcs myVcs;
  private final ChangeListManager myChangeListManager;
  private final Function<VirtualFile,Change> myChangeFunction;

  public VcsHandleType(AbstractVcs vcs) {
    super(VcsBundle.message("handle.ro.file.status.type.using.vcs", vcs.getDisplayName()), true);
    myVcs = vcs;
    myChangeListManager = ChangeListManager.getInstance(myVcs.getProject());
    myChangeFunction = new NullableFunction<VirtualFile, Change>() {
      @Override
      public Change fun(VirtualFile file) {
        return myChangeListManager.getChange(file);
      }
    };
  }

  public void processFiles(final Collection<VirtualFile> files, @Nullable final String changelist) {
    try {
      EditFileProvider provider = myVcs.getEditFileProvider();
      assert provider != null;
      provider.editFiles(VfsUtil.toVirtualFileArray(files));
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
    if (changelist != null) {
      myChangeListManager.invokeAfterUpdate(new Runnable() {
        @Override
        public void run() {
          LocalChangeList list = myChangeListManager.findChangeList(changelist);
          if (list != null) {
            List<Change> changes = ContainerUtil.mapNotNull(files, myChangeFunction);
            myChangeListManager.moveChangesTo(list, changes.toArray(new Change[changes.size()]));
          }
        }
      }, InvokeAfterUpdateMode.SILENT, "", ModalityState.NON_MODAL);
    }
  }

  @Override
  public List<String> getChangelists() {
    return ContainerUtil.map(myChangeListManager.getChangeLists(), FUNCTION);
  }

  @Override
  public String getDefaultChangelist() {
    return myChangeListManager.getDefaultListName();
  }
}
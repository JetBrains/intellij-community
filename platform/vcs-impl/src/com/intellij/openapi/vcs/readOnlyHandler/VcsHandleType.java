// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.VfsUtilCore;
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
  private static final Function<LocalChangeList,String> FUNCTION = list -> list.getName();
  private final AbstractVcs myVcs;
  private final ChangeListManager myChangeListManager;
  private final Function<VirtualFile,Change> myChangeFunction;

  public VcsHandleType(AbstractVcs vcs) {
    super(VcsBundle.message("handle.ro.file.status.type.using.vcs", vcs.getDisplayName()), true);
    myVcs = vcs;
    myChangeListManager = ChangeListManager.getInstance(myVcs.getProject());
    myChangeFunction = (NullableFunction<VirtualFile, Change>)file -> myChangeListManager.getChange(file);
  }

  @Override
  public void processFiles(final Collection<? extends VirtualFile> files, @Nullable final String changelist) {
    try {
      EditFileProvider provider = myVcs.getEditFileProvider();
      assert provider != null;
      provider.editFiles(VfsUtilCore.toVirtualFileArray(files));
    }
    catch (VcsException e) {
      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.edit.file", e.getLocalizedMessage()),
                               VcsBundle.message("message.title.edit.files"));
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (final VirtualFile file : files) {
        file.refresh(false, false);
      }
    });
    if (changelist != null) {
      myChangeListManager.invokeAfterUpdate(() -> {
        LocalChangeList list = myChangeListManager.findChangeList(changelist);
        if (list != null) {
          List<Change> changes = ContainerUtil.mapNotNull(files, myChangeFunction);
          myChangeListManager.moveChangesTo(list, changes.toArray(new Change[0]));
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
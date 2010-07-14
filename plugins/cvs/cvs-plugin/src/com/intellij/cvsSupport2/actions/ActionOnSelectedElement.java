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
package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsstatuses.CvsStatusProvider;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;

/**
 * author: lesya
 */

public abstract class ActionOnSelectedElement extends AbstractAction {

  protected static File[] getAllSelectedFiles(VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    File[] selectedIOFiles = context.getSelectedIOFiles();
    ArrayList result = new ArrayList();
    if (selectedFiles != null) {
      for (int i = 0; i < selectedFiles.length; i++) {
        result.add(CvsVfsUtil.getFileFor(selectedFiles[i]));
      }
    }
    ;
    if (selectedIOFiles != null) ContainerUtil.addAll(result, selectedIOFiles);
    return (File[])result.toArray(new File[result.size()]);
  }

  protected static CvsActionVisibility.Condition FILES_HAVE_PARENT_UNDER_CVS =
    new CvsActionVisibility.Condition() {
      public boolean isPerformedOn(CvsContext context) {
        return CvsUtil.filesHaveParentUnderCvs(getAllSelectedFiles(context));
      }
    };

  protected static CvsActionVisibility.Condition FILES_ARENT_UNDER_CVS =
    new CvsActionVisibility.Condition() {
      public boolean isPerformedOn(CvsContext context) {
        return CvsUtil.filesArentUnderCvs(getAllSelectedFiles(context));
      }
    };

  public static CvsActionVisibility.Condition FILES_ARE_UNDER_CVS =
    new CvsActionVisibility.Condition() {
      public boolean isPerformedOn(CvsContext context) {
        return CvsUtil.filesAreUnderCvs(getAllSelectedFiles(context));
      }
    };

  public static CvsActionVisibility.Condition FILES_EXIST_IN_CVS =
    new CvsActionVisibility.Condition() {
      public boolean isPerformedOn(CvsContext context) {
        return CvsUtil.filesExistInCvs(getAllSelectedFiles(context));
      }
    };

  public static CvsActionVisibility.Condition FILES_ARE_NOT_DELETED =
    new CvsActionVisibility.Condition() {
      public boolean isPerformedOn(CvsContext context) {
        return CvsUtil.filesAreNotDeleted(getAllSelectedFiles(context));
      }
    };

  public static final CvsActionVisibility.Condition FILES_ARE_CHANGED = new CvsActionVisibility.Condition() {
    public boolean isPerformedOn(CvsContext context) {
      VirtualFile[] selectedFiles = context.getSelectedFiles();
      if (selectedFiles == null) return false;
      for (int i = 0; i < selectedFiles.length; i++) {
        VirtualFile selectedFile = selectedFiles[i];
        if (CvsStatusProvider.getStatus(selectedFile) == FileStatus.NOT_CHANGED) {
          return documentIsModified(selectedFile);
        }
      }
      return true;
    }
  };

  public static final CvsActionVisibility.Condition FILES_ARE_NOT_IGNORED = new CvsActionVisibility.Condition() {
    public boolean isPerformedOn(CvsContext context) {
      VirtualFile[] selectedFiles = context.getSelectedFiles();
      if (selectedFiles == null) return false;
      final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
      for (VirtualFile selectedFile : selectedFiles) {
        if (entriesManager.fileIsIgnored(selectedFile)) return false;
      }
      return true;
    }
  };

  public static final CvsActionVisibility.Condition FILES_ARE_LOCALLY_ADDED = new CvsActionVisibility.Condition() {
    public boolean isPerformedOn(CvsContext context) {
      VirtualFile[] selectedFiles = context.getSelectedFiles();
      if (selectedFiles == null) return false;
      for (VirtualFile selectedFile : selectedFiles) {
        if (!CvsUtil.fileIsLocallyAdded(selectedFile)) return false;
      }
      return true;
    }
  };


  private static boolean documentIsModified(final VirtualFile file) {
    final boolean[] result = new boolean[]{false};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document == null) {
          result[0] = false;
        }
        else {
          result[0] = document.getModificationStamp() != file.getModificationStamp();
        }
      }
    });
    return result[0];
  }


  private final CvsActionVisibility myVisibility = new CvsActionVisibility();

  public ActionOnSelectedElement(boolean startLvcsAction) {
    super(startLvcsAction);
  }

  public ActionOnSelectedElement(boolean startLvcsAction, String name, Icon icon) {
    super(startLvcsAction, name, icon);
  }


  public void update(AnActionEvent e) {
    getVisibility().applyToEvent(e);
  }

  protected CvsActionVisibility getVisibility() {
    return myVisibility;
  }
}

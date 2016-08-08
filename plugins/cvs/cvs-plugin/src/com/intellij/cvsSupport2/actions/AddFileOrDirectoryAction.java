/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.DeletedCVSDirectoryStorage;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui.AbstractAddOptionsDialog;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.OptionsDialog;

import java.util.*;

/**
 * author: lesya
 */
public class AddFileOrDirectoryAction extends ActionOnSelectedElement {

  private final String myTitle;
  private final Options myOptions;

  public static AddFileOrDirectoryAction createActionToAddNewFileAutomatically() {
    return new AddFileOrDirectoryAction(CvsBundle.getAddingFilesOperationName(), Options.ON_FILE_ADDING);
  }

  public AddFileOrDirectoryAction() {
    this(CvsBundle.getAddingFilesOperationName(), Options.ADD_ACTION);
    getVisibility().canBePerformedOnSeveralFiles();
  }

  public AddFileOrDirectoryAction(String title, Options options) {
    super(false);
    myTitle = title;
    myOptions = options;
    final CvsActionVisibility visibility = getVisibility();
    visibility.addCondition(FILES_ARENT_UNDER_CVS);
  }
  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible()) return;
    final Project project = CvsContextWrapper.createInstance(e).getProject();
    if (project == null) return;
    adjustName(CvsVcs2.getInstance(project).getAddOptions().getValue(), e);
  }

  protected String getTitle(VcsContext context) {
    return myTitle;
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    final Project project = context.getProject();
    final boolean showDialog = myOptions.isToBeShown(project) || OptionsDialog.shiftIsPressed(context.getModifiers());
    return getCvsHandler(project, context.getSelectedFiles(), showDialog, myOptions);
  }

  public static CvsHandler getDefaultHandler(Project project, VirtualFile[] files) {
    return getCvsHandler(project, files, true, Options.NULL);
  }

  private static CvsHandler getCvsHandler(final Project project,
                                          final VirtualFile[] files,
                                          final boolean showDialog,
                                          final Options dialogOptions) {
    final ArrayList<VirtualFile> filesToAdd = collectFilesToAdd(files);
    if (filesToAdd.isEmpty()) return CvsHandler.NULL;
    LOG.assertTrue(!filesToAdd.isEmpty());

    final Collection<AddedFileInfo> roots = new CreateTreeOnFileList(filesToAdd,  project).getRoots();
    if (roots.isEmpty()) {
      LOG.error(filesToAdd);
    }

    if (!showDialog) {
      return CommandCvsHandler.createAddFilesHandler(project, roots);
    }
    final Ref<CvsHandler> handler = new Ref<>();
    final Runnable runnable = () -> {
      final AbstractAddOptionsDialog dialog = AbstractAddOptionsDialog.createDialog(project, roots, dialogOptions);
      handler.set(!dialog.showAndGet() ? CvsHandler.NULL : CommandCvsHandler.createAddFilesHandler(project, roots));
    };
    ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.any());

    return handler.get();
  }

  @Override
  protected void onActionPerformed(final CvsContext context,
                                   final CvsTabbedWindow tabbedWindow, final boolean successfully, final CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    final VirtualFile[] filesToAdd = context.getSelectedFiles();
    final VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(context.getProject());
    for(VirtualFile file: filesToAdd) {
      if (file.isDirectory()) {
        dirtyScopeManager.dirDirtyRecursively(file);
      }
      else {
        dirtyScopeManager.fileDirty(file);        
      }
    }
  }

  private static ArrayList<VirtualFile> collectFilesToAdd(final VirtualFile[] files) {
    final ArrayList<VirtualFile> result = new ArrayList<>();
    for (VirtualFile file : files) {
      final List<VirtualFile> parentsToAdd = new ArrayList<>();
      VirtualFile parent = file.getParent();
      do {
        if (parent == null || CvsUtil.fileExistsInCvs(parent) || result.contains(parent)) break;
        parentsToAdd.add(parent);
        parent = parent.getParent();
      }
      while (true);

      if (parent != null) {
        result.addAll(parentsToAdd);
      }
      addFilesToCollection(result, file);
    }
    Collections.sort(result, (o1, o2) -> o1.getPath().compareTo(o2.getPath()));
    return result;
  }

  private static void addFilesToCollection(Collection<VirtualFile> collection, VirtualFile file) {
    if (DeletedCVSDirectoryStorage.isAdminDir(file)) return;
    collection.add(file);
    final VirtualFile[] children = file.getChildren();
    if (children == null) return;
    for (VirtualFile child : children) {
      addFilesToCollection(collection, child);
    }
  }

  static class CreateTreeOnFileList {
    private final Collection<VirtualFile> myFiles;
    private final Map<VirtualFile, AddedFileInfo> myResult = new HashMap<>();
    private final Project myProject;

    public CreateTreeOnFileList(Collection<VirtualFile> files, Project project) {
      myFiles = files;
      myProject = project;
      fillFileToInfoMap();
      setAllParents();
      final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
      for (final VirtualFile file : files) {
        if (entriesManager.fileIsIgnored(file) || FileTypeManager.getInstance().isFileIgnored(file)) {
          myResult.get(file).setIncluded(false);
        }
      }
      removeFromMapInfoWithParentAndResortAll();
    }

    public Collection<AddedFileInfo> getRoots() {
      return myResult.values();
    }

    private void removeFromMapInfoWithParentAndResortAll() {
      for (final VirtualFile file : myFiles) {
        if (myResult.containsKey(file)) {
          final AddedFileInfo info = myResult.get(file);
          if (info.getParent() != null) {
            myResult.remove(file);
          }
          else {
            info.sort();
          }
        }
      }
    }

    private void setAllParents() {
      for (final VirtualFile file : myFiles) {
        if (myResult.containsKey(file.getParent()) && myResult.containsKey(file)) {
          final AddedFileInfo info = myResult.get(file);
          info.setParent(myResult.get(file.getParent()));
        }
      }
    }

    private void fillFileToInfoMap() {
      for (final VirtualFile file : myFiles) {
        myResult.put(file, new AddedFileInfo(file, myProject, CvsConfiguration.getInstance(myProject)));
      }
    }
  }
}

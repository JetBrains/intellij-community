package com.intellij.cvsSupport2.actions;

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
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.OptionsDialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * author: lesya
 */
public class AddFileOrDirectoryAction extends ActionOnSelectedElement {

  private final String myTitle;
  private final Options myOptions;
  private final boolean myIsAutomaticallyAction;

  public static AddFileOrDirectoryAction createActionToAddNewFileAutomatically() {
    return new AddFileOrDirectoryAction("Adding files", Options.ON_FILE_ADDING, true);
  }

  public AddFileOrDirectoryAction() {
    this("Adding files to CVS", Options.ADD_ACTION, false);
    getVisibility().canBePerformedOnSeveralFiles();
  }

  public AddFileOrDirectoryAction(String title, Options options, boolean isAutomatically) {
    super(false);
    myTitle = title;
    myOptions = options;
    myIsAutomaticallyAction = isAutomatically;

    CvsActionVisibility visibility = getVisibility();
    visibility.addCondition(FILES_ARENT_UNDER_CVS);
    visibility.addCondition(FILES_HAVE_PARENT_UNDER_CVS);
  }
  public void update(AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isVisible())
      return;
    Project project = CvsContextWrapper.createInstance(e).getProject();
    if (project == null) return;
    adjustName(CvsVcs2.getInstance(project).getAddOptions().getValue(), e);
  }

  protected String getTitle(VcsContext context) {
    return myTitle;
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    ArrayList<VirtualFile> filesToAdd = collectFilesToAdd(context.getSelectedFiles());
    if (filesToAdd.isEmpty()) return CvsHandler.NULL;
    LOG.assertTrue(!filesToAdd.isEmpty());

    Project project = context.getProject();
    Collection<AddedFileInfo> roots = new CreateTreeOnFileList(filesToAdd,  project, !myIsAutomaticallyAction).getRoots();

    if (roots.size() == 0){
      LOG.assertTrue(false, filesToAdd.toString());
    }

    if (myOptions.isToBeShown(project) || OptionsDialog.shiftIsPressed(context.getModifiers())){
      AbstractAddOptionsDialog dialog = AbstractAddOptionsDialog.createDialog(context.getProject(),
            roots,
            myOptions);
      dialog.show();

      if (!dialog.isOK()) return CvsHandler.NULL;
    }


    return CommandCvsHandler.createAddFilesHandler(roots);
  }

  private ArrayList<VirtualFile> collectFilesToAdd(final VirtualFile[] files) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (int i = 0; i < files.length; i++) {
      VirtualFile file = files[i];
      //if (CvsEntriesManager.getInstance().fileIsIgnored(file)) continue;
      addFilesToCollection(result, file);
    }
    return result;
  }

  private void addFilesToCollection(Collection collection, VirtualFile file) {
    if (DeletedCVSDirectoryStorage.isAdminDir(file)) return;
    collection.add(file);
    VirtualFile[] children = file.getChildren();
    if (children == null) return;
    for (int i = 0; i < children.length; i++) {
      VirtualFile child = children[i];
      addFilesToCollection(collection, child);
    }
  }

  class CreateTreeOnFileList {
    private final Collection<VirtualFile> myFiles;
    private final Map<VirtualFile, AddedFileInfo> myResult
      = new HashMap<VirtualFile, AddedFileInfo>();
    private final Project myProject;
    private final boolean myShouldIncludeAllRoots;

    public CreateTreeOnFileList(Collection<VirtualFile> files, Project project, boolean shouldIncludeAllRoots) {
      myFiles = files;
      myProject = project;
      myShouldIncludeAllRoots = shouldIncludeAllRoots;
      fillFileToInfoMap();
      setAllParents();
      if (!myShouldIncludeAllRoots) {
        final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
        for (final VirtualFile file : files) {
          VirtualFile virtualFile = (VirtualFile)file;
          if (entriesManager.fileIsIgnored(virtualFile)) {
            myResult.get(virtualFile).setIncluded(false);
          }
        }
      }
      removeFromMapInfoWithParentAndResortAll();
    }

    public Collection<AddedFileInfo> getRoots() {
      return myResult.values();
    }

    private void removeFromMapInfoWithParentAndResortAll() {
      for (Iterator each = myFiles.iterator(); each.hasNext();) {
        VirtualFile root = (VirtualFile)each.next();
        if (myResult.containsKey(root)) {
          AddedFileInfo info = myResult.get(root);
          if (info.getParent() != null) {
            myResult.remove(root);
          }
          else {
            info.sort();
          }
        }
      }
    }

    private void setAllParents() {
      for (Iterator each = myFiles.iterator(); each.hasNext();) {
        VirtualFile root = (VirtualFile)each.next();
        if (myResult.containsKey(root.getParent()) && myResult.containsKey(root)) {
          AddedFileInfo info = myResult.get(root);
          info.setParent(myResult.get(root.getParent()));
        }
      }
    }

    private void fillFileToInfoMap() {
      for (Iterator each = myFiles.iterator(); each.hasNext();) {
        VirtualFile file = (VirtualFile)each.next();
        myResult.put(file, new AddedFileInfo(file, CvsConfiguration.getInstance(myProject)));
      }
    }


  }

}

package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.ui.experts.SelectLocationStep;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import javax.swing.*;

/**
 * @author lesya
 */
public class SelectImportLocationStep extends SelectLocationStep {
  private final ImportTree myImportTree;

  public SelectImportLocationStep(String description, 
                                  CvsWizard wizard, 
                                  Project project,
                                  VirtualFile selectedFile) {
    super(description, wizard, project);
    myImportTree = new ImportTree(project, myFileSystemTree, wizard);
    init();
    JTree tree = myFileSystemTree.getTree();
    tree.setCellRenderer(myImportTree);
    if (selectedFile != null)
      myFileSystemTree.select(selectedFile, null);
  }

  protected AnAction[] getActions() {
    return new AnAction[]{
      myImportTree.createExcludeAction(),
      myImportTree.createIncludeAction()
    };
  }

  public IIgnoreFileFilter getIgnoreFileFilter() {
    return myImportTree.getIgnoreFileFilter();
  }

  public boolean nextIsEnabled() {
    return super.nextIsEnabled() && !myImportTree.isExcluded(myFileSystemTree.getSelectedFile());
  }
}

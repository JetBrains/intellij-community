/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

  public SelectImportLocationStep(String description, CvsWizard wizard, Project project, VirtualFile selectedFile) {
    super(description, wizard, project, true);
    myImportTree = new ImportTree(project, myFileSystemTree, wizard);
    init();
    JTree tree = myFileSystemTree.getTree();
    tree.setCellRenderer(myImportTree);
    if (selectedFile != null && selectedFile.isDirectory()) {
      myFileSystemTree.select(selectedFile, null);
    }
    else if (project != null) {
      myFileSystemTree.select(project.getBaseDir(), null);
    }
  }

  @Override
  protected AnAction[] getActions() {
    return new AnAction[]{
      myImportTree.createExcludeAction(),
      myImportTree.createIncludeAction()
    };
  }

  public IIgnoreFileFilter getIgnoreFileFilter() {
    return myImportTree.getIgnoreFileFilter();
  }

  @Override
  public boolean nextIsEnabled() {
    return super.nextIsEnabled() && !myImportTree.isExcluded(myFileSystemTree.getSelectedFile());
  }
}

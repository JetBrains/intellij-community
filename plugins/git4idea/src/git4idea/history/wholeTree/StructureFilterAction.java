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
package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.ui.VcsStructureChooser;

import java.util.Collection;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 2/3/11
 *         Time: 4:29 PM
 */
public class StructureFilterAction extends BasePopupAction {
  public static final String ALL = "All";
  public static final String STRUCTURE = "Structure:";
  public static final String FILTER = "(filter)";
  private final DumbAwareAction myAll;
  private final DumbAwareAction mySelect;
  private final StructureFilterI myStructureFilterI;

  public StructureFilterAction(Project project, final StructureFilterI structureFilterI) {
    super(project, STRUCTURE, "Structure");
    myStructureFilterI = structureFilterI;
    myAll = new DumbAwareAction(ALL) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLabel.setText(ALL);
        myPanel.setToolTipText(STRUCTURE + " " + ALL);
        structureFilterI.allSelected();
      }
    };
    mySelect = new DumbAwareAction("Select...") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final VcsStructureChooser vcsStructureChooser =
          new VcsStructureChooser(myProject, "Select folders to filter by", structureFilterI.getSelected(),
                                  structureFilterI.getRoots());
        vcsStructureChooser.show();
        if (vcsStructureChooser.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) return;
        final Collection<VirtualFile> files = vcsStructureChooser.getSelectedFiles();
        final Map<VirtualFile,String> modulesSet = vcsStructureChooser.getModulesSet();
        final String text = getText(files, modulesSet);
        final String toolTip = getTooltip(files, modulesSet);
        myPanel.setToolTipText(wrapTooltip(toolTip));
        myLabel.setText(text);
        structureFilterI.select(files);
      }
    };
    myLabel.setText(ALL);
  }

  private String wrapTooltip(String toolTip) {
    return "<html><b>" + STRUCTURE + "</b><br>" + toolTip + "</html>";
  }

  private String getTooltip(Collection<VirtualFile> files, Map<VirtualFile, String> modulesSet) {
    String toolTip;
    final StringBuilder sb = new StringBuilder();
    for (VirtualFile file : files) {
      sb.append("<br><b>");
      final String module = modulesSet.get(file);
      final String name = module == null ? file.getName() : module;
      sb.append(name).append("</b> (").append(file.getPath()).append(")");
    }
    toolTip = sb.toString();
    return toolTip;
  }

  private String getText(Collection<VirtualFile> files, Map<VirtualFile, String> modulesSet) {
    String text;
    if (files.size() == 1) {
      final VirtualFile file = files.iterator().next();
      final String module = modulesSet.get(file);
      text = module == null ? file.getName() : module;
    }
    else {
      text = FILTER;
    }
    text = text.length() > 20 ? FILTER : text;
    return text;
  }

  @Override
  protected void createActions(Consumer<AnAction> actionConsumer) {
    actionConsumer.consume(myAll);
    actionConsumer.consume(mySelect);
  }

  public void setPreset() {
    final Collection<VirtualFile> selected = myStructureFilterI.getSelected();

    if (myStructureFilterI.isAllSelected()) {
      myLabel.setText(ALL);
      myPanel.setToolTipText(STRUCTURE + " " + ALL);
    } else {
      final VcsStructureChooser vcsStructureChooser =
        new VcsStructureChooser(myProject, "Select folders to filter by", myStructureFilterI.getSelected(),
                                myStructureFilterI.getRoots());
      final Map<VirtualFile,String> modulesSet = vcsStructureChooser.getModulesSet();
      final String text = getText(selected, modulesSet);
      final String toolTip = getTooltip(selected, modulesSet);
      myPanel.setToolTipText(wrapTooltip(toolTip));
      myLabel.setText(text);
      Disposer.dispose(vcsStructureChooser.getDisposable());
    }
  }
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogStructureFilter;
import com.intellij.vcs.log.data.VcsLogStructureFilterImpl;
import com.intellij.vcs.log.ui.VcsStructureChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class StructureFilterPopupComponent extends FilterPopupComponent<VcsLogStructureFilter> {

  private static final int FILTER_LABEL_LENGTH = 20;

  public StructureFilterPopupComponent(@NotNull FilterModel<VcsLogStructureFilter> filterModel) {
    super("Structure", filterModel);
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogStructureFilter filter) {
    Collection<VirtualFile> files = getAllFiles(myFilterModel.getDataPack(), filter);
    if (files.size() == 0) {
      return ALL;
    }
    else if (files.size() == 1) {
      VirtualFile file = files.iterator().next();
      return StringUtil.shortenPathWithEllipsis(file.getPresentableUrl(), FILTER_LABEL_LENGTH);
    }
    else {
      return files.size() + " items";
    }
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogStructureFilter filter) {
    return getToolTip(getAllFiles(myFilterModel.getDataPack(), filter));
  }

  @NotNull
  private static Collection<VirtualFile> getAllFiles(@NotNull VcsLogDataPack dataPack, @NotNull VcsLogStructureFilter filter) {
    Collection<VirtualFile> result = ContainerUtil.newArrayList();
    for (VirtualFile root : dataPack.getLogProviders().keySet()) {
      result.addAll(filter.getFiles(root));
    }
    return result;
  }

  @Override
  protected ActionGroup createActionGroup() {
    return new DefaultActionGroup(createAllAction(), new SelectAction());
  }

  @NotNull
  private static String getToolTip(@NotNull Collection<VirtualFile> files) {
    List<VirtualFile> filesToDisplay = new ArrayList<VirtualFile>(files);
    if (files.size() > 10) {
      filesToDisplay = filesToDisplay.subList(0, 10);
    }
    String tooltip = StringUtil.join(filesToDisplay, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile file) {
        return file.getPresentableUrl();
      }
    }, "\n");
    if (files.size() > 10) {
      tooltip += "\n...";
    }
    return tooltip;
  }

  private class SelectAction extends DumbAwareAction {
    SelectAction() {
      super("Select...");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      VcsLogDataPack dataPack = myFilterModel.getDataPack();
      VcsLogStructureFilter filter = myFilterModel.getFilter();
      Collection<VirtualFile> files = filter == null ? Collections.<VirtualFile>emptySet() : getAllFiles(dataPack, filter);
      Set<VirtualFile> roots = dataPack.getLogProviders().keySet();
      VcsStructureChooser chooser = new VcsStructureChooser(project, "Select Files or Folders to Filter", files,
                                                            new ArrayList<VirtualFile>(roots));
      if (chooser.showAndGet()) {
        myFilterModel.setFilter(new VcsLogStructureFilterImpl(chooser.getSelectedFiles(), roots));
      }
    }
  }
}

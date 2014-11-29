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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogRootFilterImpl;
import com.intellij.vcs.log.data.VcsLogFileFilter;
import com.intellij.vcs.log.data.VcsLogStructureFilterImpl;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsStructureChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class StructureFilterPopupComponent extends FilterPopupComponent<VcsLogFileFilter> {
  private static final int FILTER_LABEL_LENGTH = 20;
  @NotNull private final VcsLogColorManager myColorManager;

  public StructureFilterPopupComponent(@NotNull FilterModel<VcsLogFileFilter> filterModel, @NotNull VcsLogColorManager colorManager) {
    super("Structure", filterModel);
    myColorManager = colorManager;
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogFileFilter filter) {
    Collection<VirtualFile> files = getAllFiles(filter);
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
  protected String getToolTip(@NotNull VcsLogFileFilter filter) {
    return getToolTip(getAllFiles(filter));
  }

  @NotNull
  private static Collection<VirtualFile> getAllFiles(@NotNull VcsLogFileFilter filter) {
    Collection<VirtualFile> result = ContainerUtil.newArrayList();
    if (filter.getRootFilter() != null) result.addAll(filter.getRootFilter().getRoots());
    if (filter.getStructureFilter() != null) result.addAll(filter.getStructureFilter().getFiles());
    return result;
  }

  @Override
  protected ActionGroup createActionGroup() {
    Set<VirtualFile> roots = myFilterModel.getDataPack().getLogProviders().keySet();

    List<AnAction> actions = new ArrayList<AnAction>();
    if (roots.size() <= 10) {
      for (VirtualFile root : roots) {
        actions.add(new SelectVisibleRootAction(root));
      }
    }
    return new DefaultActionGroup(createAllAction(), new Separator(), new DefaultActionGroup(actions), new Separator(), new SelectAction());
  }

  private boolean isVisible(@NotNull VirtualFile root) {
    VcsLogFileFilter filter = myFilterModel.getFilter();
    if (filter != null && filter.getRootFilter() != null) {
      return filter.getRootFilter().getRoots().contains(root);
    }
    else {
      return true;
    }
  }

  private void setVisible(@NotNull VirtualFile root, boolean visible) {
    Set<VirtualFile> roots = myFilterModel.getDataPack().getLogProviders().keySet();

    VcsLogFileFilter previousFilter = myFilterModel.getFilter();
    VcsLogRootFilter rootFilter = previousFilter != null ? previousFilter.getRootFilter() : null;

    Collection<VirtualFile> visibleRoots;
    if (rootFilter == null) {
      if (visible) {
        visibleRoots = roots;
      }
      else {
        visibleRoots = ContainerUtil.subtract(roots, Collections.singleton(root));
      }
    }
    else {
      if (visible) {
        visibleRoots = ContainerUtil.union(new HashSet<VirtualFile>(rootFilter.getRoots()), Collections.singleton(root));
      }
      else {
        visibleRoots = ContainerUtil.subtract(rootFilter.getRoots(), Collections.singleton(root));
      }
    }
    myFilterModel.setFilter(new VcsLogFileFilter(previousFilter != null ? previousFilter.getStructureFilter() : null, new VcsLogRootFilterImpl(visibleRoots)));
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

  private class SelectVisibleRootAction extends ToggleAction {
    @NotNull private final VirtualFile myRoot;

    private SelectVisibleRootAction(@NotNull VirtualFile root) {
      super(root.getName());
      myRoot = root;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return isVisible(myRoot);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setVisible(myRoot, state);
    }
  }

  private class SelectAction extends DumbAwareAction {
    SelectAction() {
      super("Select...");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      VcsLogDataPack dataPack = myFilterModel.getDataPack();
      VcsLogFileFilter filter = myFilterModel.getFilter();
      VcsLogRootFilter rootFilter = filter == null ? null : filter.getRootFilter();
      Collection<VirtualFile> files = filter == null || filter.getStructureFilter() == null ? Collections.<VirtualFile>emptySet() : filter.getStructureFilter().getFiles();
      VcsStructureChooser chooser = new VcsStructureChooser(project, "Select Files or Folders to Filter", files,
                                                            new ArrayList<VirtualFile>(dataPack.getLogProviders().keySet()));
      if (chooser.showAndGet()) {
        myFilterModel.setFilter(new VcsLogFileFilter(new VcsLogStructureFilterImpl(new HashSet<VirtualFile>(chooser.getSelectedFiles())), rootFilter));
      }
    }
  }
}

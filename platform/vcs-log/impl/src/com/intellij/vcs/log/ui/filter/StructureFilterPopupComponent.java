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
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogStructureFilter;
import com.intellij.vcs.log.data.VcsLogStructureFilterImpl;
import com.intellij.vcs.log.ui.VcsStructureChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class StructureFilterPopupComponent extends FilterPopupComponent<VcsLogStructureFilter> {

  public static final int FILTER_LABEL_LENGTH = 20;
  @NotNull private final Collection<VirtualFile> myRoots;
  @NotNull private final Collection<VirtualFile> myFiles;

  public StructureFilterPopupComponent(@NotNull VcsLogClassicFilterUi filterUi, @NotNull Collection<VirtualFile> roots) {
    super(filterUi, "Structure");
    myRoots = roots;
    myFiles = ContainerUtil.newArrayList();
  }

  @Override
  protected ActionGroup createActionGroup() {
    return new DefaultActionGroup(createAllAction(), new SelectAction());
  }

  @Nullable
  @Override
  protected VcsLogStructureFilter getFilter() {
    return getValue() == ALL || myFiles.isEmpty() ? null : new VcsLogStructureFilterImpl(myFiles, myRoots);
  }

  private void setValue(@NotNull Collection<VirtualFile> files) {
    if (files.size() == 0) {
      setValue(ALL);
    }
    else if (files.size() == 1) {
      VirtualFile file = files.iterator().next();
      setValue(StringUtil.shortenPathWithEllipsis(file.getPresentableUrl(), FILTER_LABEL_LENGTH), getTooltip(files));
    }
    else {
      setValue(files.size() + " items", getTooltip(files));
    }
  }

  @NotNull
  private static String getTooltip(@NotNull Collection<VirtualFile> files) {
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
    public void actionPerformed(AnActionEvent e) {
      Project project = e.getProject();
      assert project != null;
      VcsStructureChooser chooser = new VcsStructureChooser(project, "Select Files or Folders to Filter", myFiles,
                                                            new ArrayList<VirtualFile>(myRoots));
      if (chooser.showAndGet()) {
        myFiles.clear();
        myFiles.addAll(chooser.getSelectedFiles());
        setValue(myFiles);
        applyFilters();
      }
    }

  }

}

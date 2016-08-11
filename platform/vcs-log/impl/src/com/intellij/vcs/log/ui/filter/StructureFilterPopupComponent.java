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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogRootFilterImpl;
import com.intellij.vcs.log.VcsLogStructureFilter;
import com.intellij.vcs.log.data.VcsLogStructureFilterImpl;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

class StructureFilterPopupComponent extends FilterPopupComponent<VcsLogFileFilter> {
  private static final int FILTER_LABEL_LENGTH = 30;
  private static final int CHECKBOX_ICON_SIZE = 15;
  public static final FileByNameComparator FILE_BY_NAME_COMPARATOR = new FileByNameComparator();
  public static final FileByPathComparator FILE_BY_PATH_COMPARATOR = new FileByPathComparator();
  public static final FilePathByNameComparator FILE_PATH_BY_NAME_COMPARATOR = new FilePathByNameComparator();
  public static final FilePathByPathComparator FILE_PATH_BY_PATH_COMPARATOR = new FilePathByPathComparator();
  @NotNull private final VcsLogColorManager myColorManager;
  private final FixedSizeQueue<VcsLogStructureFilter> myHistory = new FixedSizeQueue<>(5);

  public StructureFilterPopupComponent(@NotNull FilterModel<VcsLogFileFilter> filterModel, @NotNull VcsLogColorManager colorManager) {
    super("Paths", filterModel);
    myColorManager = colorManager;
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogFileFilter filter) {
    Collection<VirtualFile> roots = filter.getRootFilter() == null ? getAllRoots() : filter.getRootFilter().getRoots();
    Collection<FilePath> files =
      filter.getStructureFilter() == null ? Collections.<FilePath>emptySet() : filter.getStructureFilter().getFiles();
    Collection<VirtualFile> visibleRoots =
      VcsLogUtil.getAllVisibleRoots(getAllRoots(), filter.getRootFilter(), filter.getStructureFilter());

    if (files.isEmpty()) {
      return getTextFromRoots(roots, "roots", true, visibleRoots.size() == getAllRoots().size());
    }
    else {
      return getTextFromFilePaths(files, "folders", false, files.isEmpty());
    }
  }

  private static String getTextFromRoots(@NotNull Collection<VirtualFile> files,
                                         @NotNull String category,
                                         final boolean shorten,
                                         boolean full) {
    return getText(files, category, shorten ? FILE_BY_NAME_COMPARATOR : FILE_BY_PATH_COMPARATOR,
                   file -> shorten ? file.getName() : StringUtil.shortenPathWithEllipsis(file.getPresentableUrl(), FILTER_LABEL_LENGTH),
                   full);
  }

  private static String getTextFromFilePaths(@NotNull Collection<FilePath> files,
                                             @NotNull String category,
                                             final boolean shorten,
                                             boolean full) {
    return getText(files, category, shorten ? FILE_PATH_BY_NAME_COMPARATOR : FILE_PATH_BY_PATH_COMPARATOR,
                   file -> shorten ? file.getName() : StringUtil.shortenPathWithEllipsis(file.getPresentableUrl(), FILTER_LABEL_LENGTH),
                   full);
  }

  private static <F> String getText(@NotNull Collection<F> files,
                                    @NotNull String category,
                                    @NotNull Comparator<F> comparator,
                                    @NotNull NotNullFunction<F, String> getText,
                                    boolean full) {
    if (full) {
      return ALL;
    }
    else if (files.isEmpty()) {
      return "No " + category;
    }
    else {
      F firstFile = Collections.min(files, comparator);
      String firstFileName = getText.fun(firstFile);
      if (files.size() == 1) {
        return firstFileName;
      }
      else {
        return firstFileName + " + " + (files.size() - 1);
      }
    }
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogFileFilter filter) {
    return getToolTip(filter.getRootFilter() == null ? getAllRoots() : filter.getRootFilter().getRoots(),
                      filter.getStructureFilter() == null ? Collections.<FilePath>emptySet() : filter.getStructureFilter().getFiles());
  }

  @NotNull
  private String getToolTip(@NotNull Collection<VirtualFile> roots, @NotNull Collection<FilePath> files) {
    String tooltip = "";
    if (roots.isEmpty()) {
      tooltip += "No Roots Selected";
    }
    else if (roots.size() != getAllRoots().size()) {
      tooltip += "Roots:\n" + getTooltipTextForRoots(roots, true);
    }
    if (!files.isEmpty()) {
      if (!tooltip.isEmpty()) tooltip += "\n";
      tooltip += "Folders:\n" + getTooltipTextForFilePaths(files, false);
    }
    return tooltip;
  }

  private static String getTooltipTextForRoots(Collection<VirtualFile> files, final boolean shorten) {
    return getTooltipTextForFiles(files, shorten ? FILE_BY_NAME_COMPARATOR : FILE_BY_PATH_COMPARATOR,
                                  file -> shorten ? file.getName() : file.getPresentableUrl());
  }

  private static String getTooltipTextForFilePaths(Collection<FilePath> files, final boolean shorten) {
    return getTooltipTextForFiles(files, shorten ? FILE_PATH_BY_NAME_COMPARATOR : FILE_PATH_BY_PATH_COMPARATOR,
                                  file -> shorten ? file.getName() : file.getPresentableUrl());
  }

  private static <F> String getTooltipTextForFiles(@NotNull Collection<F> files,
                                                   @NotNull Comparator<F> comparator,
                                                   @NotNull NotNullFunction<F, String> getText) {
    List<F> filesToDisplay = ContainerUtil.sorted(files, comparator);
    if (files.size() > 10) {
      filesToDisplay = filesToDisplay.subList(0, 10);
    }
    String tooltip = StringUtil.join(filesToDisplay, getText, "\n");
    if (files.size() > 10) {
      tooltip += "\n...";
    }
    return tooltip;
  }

  @Override
  protected ActionGroup createActionGroup() {
    Set<VirtualFile> roots = getAllRoots();

    List<AnAction> rootActions = new ArrayList<>();
    if (myColorManager.isMultipleRoots()) {
      for (VirtualFile root : ContainerUtil.sorted(roots, FILE_BY_NAME_COMPARATOR)) {
        rootActions.add(new SelectVisibleRootAction(root));
      }
    }
    List<AnAction> structureActions = new ArrayList<>();
    for (VcsLogStructureFilter filter : myHistory) {
      structureActions.add(new SelectFromHistoryAction(filter));
    }

    if (roots.size() > 15) {
      return new DefaultActionGroup(createAllAction(), new SelectFoldersAction(),
                                    new Separator("Recent"), new DefaultActionGroup(structureActions),
                                    new Separator("Roots"), new DefaultActionGroup(rootActions));
    }
    else {
      return new DefaultActionGroup(createAllAction(), new SelectFoldersAction(),
                                    new Separator("Roots"), new DefaultActionGroup(rootActions),
                                    new Separator("Recent"), new DefaultActionGroup(structureActions));
    }
  }

  private Set<VirtualFile> getAllRoots() {
    return myFilterModel.getDataPack().getLogProviders().keySet();
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
    Set<VirtualFile> roots = getAllRoots();

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
        visibleRoots = ContainerUtil.union(new HashSet<>(rootFilter.getRoots()), Collections.singleton(root));
      }
      else {
        visibleRoots = ContainerUtil.subtract(rootFilter.getRoots(), Collections.singleton(root));
      }
    }
    myFilterModel.setFilter(new VcsLogFileFilter(null, new VcsLogRootFilterImpl(visibleRoots)));
  }

  private void setVisibleOnly(@NotNull VirtualFile root) {
    myFilterModel.setFilter(new VcsLogFileFilter(null, new VcsLogRootFilterImpl(Collections.singleton(root))));
  }

  private static String getStructureActionText(@NotNull VcsLogStructureFilter filter) {
    return getTextFromFilePaths(filter.getFiles(), "items", false, filter.getFiles().isEmpty());
  }

  private static class FileByNameComparator implements Comparator<VirtualFile> {
    @Override
    public int compare(VirtualFile o1, VirtualFile o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }

  private static class FileByPathComparator implements Comparator<VirtualFile> {
    @Override
    public int compare(VirtualFile o1, VirtualFile o2) {
      return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
    }
  }

  private static class FilePathByNameComparator implements Comparator<FilePath> {
    @Override
    public int compare(FilePath o1, FilePath o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }

  private static class FilePathByPathComparator implements Comparator<FilePath> {
    @Override
    public int compare(FilePath o1, FilePath o2) {
      return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
    }
  }

  private class SelectVisibleRootAction extends ToggleAction implements DumbAware, KeepingPopupOpenAction {
    @NotNull private final CheckboxColorIcon myIcon;
    @NotNull private final VirtualFile myRoot;

    private SelectVisibleRootAction(@NotNull VirtualFile root) {
      super(root.getName(), root.getPresentableUrl(), null);
      myRoot = root;
      myIcon = new CheckboxColorIcon(CHECKBOX_ICON_SIZE, VcsLogGraphTable.getRootBackgroundColor(myRoot, myColorManager));
      getTemplatePresentation().setIcon(EmptyIcon.create(CHECKBOX_ICON_SIZE)); // see PopupFactoryImpl.calcMaxIconSize
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return isVisible(myRoot);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (!isEnabled()) {
        setVisibleOnly(myRoot);
      }
      else {
        if ((e.getModifiers() & getMask()) != 0) {
          setVisibleOnly(myRoot);
        }
        else {
          setVisible(myRoot, state);
        }
      }
    }

    @JdkConstants.InputEventMask
    private int getMask() {
      return SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      updateIcon();
      e.getPresentation().setIcon(myIcon);
      e.getPresentation().putClientProperty(TOOL_TIP_TEXT_KEY, KeyEvent.getKeyModifiersText(getMask()) +
                                                               "+Click to see only \"" +
                                                               e.getPresentation().getText() +
                                                               "\"");
    }

    private void updateIcon() {
      myIcon.prepare(isVisible(myRoot) && isEnabled());
    }

    private boolean isEnabled() {
      return myFilterModel.getFilter() == null || (myFilterModel.getFilter().getStructureFilter() == null);
    }
  }

  private static class CheckboxColorIcon extends ColorIcon {
    private final int mySize;
    private boolean mySelected = false;
    private SizedIcon mySizedIcon;

    public CheckboxColorIcon(int size, @NotNull Color color) {
      super(size, color);
      mySize = size;
      mySizedIcon = new SizedIcon(PlatformIcons.CHECK_ICON_SMALL, mySize, mySize);
    }

    public void prepare(boolean selected) {
      mySelected = selected;
    }

    @Override
    public void paintIcon(Component component, Graphics g, int i, int j) {
      super.paintIcon(component, g, i, j);
      if (mySelected) {
        mySizedIcon.paintIcon(component, g, i, j);
      }
    }
  }

  private class SelectFoldersAction extends DumbAwareAction {
    public static final String STRUCTURE_FILTER_TEXT = "Select Folders...";

    SelectFoldersAction() {
      super(STRUCTURE_FILTER_TEXT);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      VcsLogDataPack dataPack = myFilterModel.getDataPack();
      VcsLogFileFilter filter = myFilterModel.getFilter();

      Collection<VirtualFile> files;
      if (filter == null || filter.getStructureFilter() == null) {
        files = Collections.emptySet();
      }
      else {
        files = ContainerUtil.mapNotNull(filter.getStructureFilter().getFiles(), filePath -> {
          // for now, ignoring non-existing paths
          return filePath.getVirtualFile();
        });
      }

      VcsStructureChooser chooser = new VcsStructureChooser(project, "Select Files or Folders to Filter by", files,
                                                            new ArrayList<>(dataPack.getLogProviders().keySet()));
      if (chooser.showAndGet()) {
        VcsLogStructureFilterImpl structureFilter = new VcsLogStructureFilterImpl(new HashSet<VirtualFile>(chooser.getSelectedFiles()));
        myFilterModel.setFilter(new VcsLogFileFilter(structureFilter, null));
        myHistory.add(structureFilter);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
  }

  private class SelectFromHistoryAction extends ToggleAction {
    @NotNull private final VcsLogStructureFilter myFilter;
    @NotNull private final Icon myIcon;
    @NotNull private final Icon myEmptyIcon;

    private SelectFromHistoryAction(@NotNull VcsLogStructureFilter filter) {
      super(getStructureActionText(filter), getTooltipTextForFilePaths(filter.getFiles(), false).replace("\n", " "), null);
      myFilter = filter;
      myIcon = new SizedIcon(PlatformIcons.CHECK_ICON_SMALL, CHECKBOX_ICON_SIZE, CHECKBOX_ICON_SIZE);
      myEmptyIcon = EmptyIcon.create(CHECKBOX_ICON_SIZE);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myFilterModel.getFilter() != null && myFilterModel.getFilter().getStructureFilter() == myFilter;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myFilterModel.setFilter(new VcsLogFileFilter(myFilter, null));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      Presentation presentation = e.getPresentation();
      if (isSelected(e)) {
        presentation.setIcon(myIcon);
      }
      else {
        presentation.setIcon(myEmptyIcon);
      }
    }
  }

  private static class FixedSizeQueue<T> implements Iterable<T> {
    private final LinkedList<T> myQueue = new LinkedList<>();
    private final int maxSize;

    public FixedSizeQueue(int maxSize) {
      this.maxSize = maxSize;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
      return ContainerUtil.reverse(myQueue).iterator();
    }

    public void add(T t) {
      myQueue.add(t);
      if (myQueue.size() > maxSize) {
        myQueue.poll();
      }
    }
  }
}

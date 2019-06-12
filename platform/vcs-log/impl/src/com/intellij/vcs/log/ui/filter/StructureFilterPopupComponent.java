// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

class StructureFilterPopupComponent extends FilterPopupComponent<FilterPair<VcsLogStructureFilter, VcsLogRootFilter>,
  FilterModel.PairFilterModel<VcsLogStructureFilter, VcsLogRootFilter>> {

  private static final int FILTER_LABEL_LENGTH = 30;
  private static final int CHECKBOX_ICON_SIZE = 15;
  private static final FileByNameComparator FILE_BY_NAME_COMPARATOR = new FileByNameComparator();
  private static final FilePathByPathComparator FILE_PATH_BY_PATH_COMPARATOR = new FilePathByPathComparator();

  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogColorManager myColorManager;

  StructureFilterPopupComponent(@NotNull MainVcsLogUiProperties uiProperties,
                                @NotNull FilterModel.PairFilterModel<VcsLogStructureFilter, VcsLogRootFilter> filterModel,
                                @NotNull VcsLogColorManager colorManager) {
    super("Paths", filterModel);
    myUiProperties = uiProperties;
    myColorManager = colorManager;
  }

  private static VcsLogRootFilter getRootFilter(@Nullable FilterPair<VcsLogStructureFilter, VcsLogRootFilter> filter) {
    if (filter == null) return null;
    return filter.getFilter2();
  }

  private static VcsLogStructureFilter getStructureFilter(@Nullable FilterPair<VcsLogStructureFilter, VcsLogRootFilter> filter) {
    if (filter == null) return null;
    return filter.getFilter1();
  }

  @NotNull
  private Collection<VirtualFile> getFilterRoots(@Nullable VcsLogRootFilter filter) {
    return filter != null ? filter.getRoots() : getAllRoots();
  }

  @NotNull
  private static Collection<FilePath> getFilterFiles(@Nullable VcsLogStructureFilter filter) {
    return filter != null ? filter.getFiles() : Collections.emptySet();
  }

  @NotNull
  @Override
  protected String getText(@NotNull FilterPair<VcsLogStructureFilter, VcsLogRootFilter> filter) {
    VcsLogRootFilter rootFilter = getRootFilter(filter);
    VcsLogStructureFilter structureFilter = getStructureFilter(filter);
    Collection<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(getAllRoots(), rootFilter, structureFilter);

    Collection<VirtualFile> roots = getFilterRoots(rootFilter);
    Collection<FilePath> files = getFilterFiles(structureFilter);
    if (files.isEmpty()) {
      return getTextFromRoots(roots, visibleRoots.size() == getAllRoots().size());
    }
    return getTextFromFilePaths(files, "folders", false);
  }

  @NotNull
  private static String getTextFromRoots(@NotNull Collection<? extends VirtualFile> files,
                                         boolean full) {
    return getText(files, "roots", FILE_BY_NAME_COMPARATOR, VirtualFile::getName, full);
  }

  @NotNull
  private static String getTextFromFilePaths(@NotNull Collection<? extends FilePath> files,
                                             @NotNull String category,
                                             boolean full) {
    return getText(files, category, FILE_PATH_BY_PATH_COMPARATOR,
                   file -> StringUtil.shortenPathWithEllipsis(file.getPresentableUrl(), FILTER_LABEL_LENGTH), full);
  }

  @NotNull
  private static <F> String getText(@NotNull Collection<? extends F> files,
                                    @NotNull String category,
                                    @NotNull Comparator<? super F> comparator,
                                    @NotNull NotNullFunction<? super F, String> getText,
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
      return firstFileName + " + " + (files.size() - 1);
    }
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull FilterPair<VcsLogStructureFilter, VcsLogRootFilter> filter) {
    return getToolTip(getFilterRoots(getRootFilter(filter)), getFilterFiles(getStructureFilter(filter)));
  }

  @NotNull
  private String getToolTip(@NotNull Collection<? extends VirtualFile> roots, @NotNull Collection<? extends FilePath> files) {
    String tooltip = "";
    if (roots.isEmpty()) {
      tooltip += "No Roots Selected";
    }
    else if (roots.size() != getAllRoots().size()) {
      tooltip += "Roots:\n" + getTooltipTextForRoots(roots);
    }
    if (!files.isEmpty()) {
      if (!tooltip.isEmpty()) tooltip += "\n";
      tooltip += "Folders:\n" + getTooltipTextForFilePaths(files);
    }
    return tooltip;
  }

  @NotNull
  private static String getTooltipTextForRoots(@NotNull Collection<? extends VirtualFile> files) {
    return getTooltipTextForFiles(files, FILE_BY_NAME_COMPARATOR, VirtualFile::getName);
  }

  @NotNull
  private static String getTooltipTextForFilePaths(@NotNull Collection<? extends FilePath> files) {
    return getTooltipTextForFiles(files, FILE_PATH_BY_PATH_COMPARATOR, FilePath::getPresentableUrl);
  }

  @NotNull
  private static <F> String getTooltipTextForFiles(@NotNull Collection<? extends F> files,
                                                   @NotNull Comparator<? super F> comparator,
                                                   @NotNull NotNullFunction<? super F, String> getText) {
    List<F> filesToDisplay = ContainerUtil.sorted(files, comparator);
    filesToDisplay = ContainerUtil.getFirstItems(filesToDisplay, 10);
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
    for (VcsLogStructureFilter filter : getRecentFilters()) {
      structureActions.add(new SelectFromHistoryAction(filter));
    }

    if (roots.size() > 15) {
      return new DefaultActionGroup(createAllAction(), new SelectFoldersAction(),
                                    new Separator("Recent"), new DefaultActionGroup(structureActions),
                                    new Separator("Roots"), new DefaultActionGroup(rootActions));
    }
    return new DefaultActionGroup(createAllAction(), new SelectFoldersAction(),
                                  new Separator("Roots"), new DefaultActionGroup(rootActions),
                                  new Separator("Recent"), new DefaultActionGroup(structureActions));
  }

  @NotNull
  private List<VcsLogStructureFilter> getRecentFilters() {
    List<List<String>> filterValues = myUiProperties.getRecentlyFilteredGroups(myName);
    return ContainerUtil.map2List(filterValues, values -> VcsLogClassicFilterUi.FileFilterModel.createStructureFilter(values));
  }

  private Set<VirtualFile> getAllRoots() {
    return myFilterModel.getDataPack().getLogProviders().keySet();
  }

  private boolean isVisible(@NotNull VirtualFile root) {
    VcsLogRootFilter rootFilter = getRootFilter(myFilterModel.getFilter());
    if (rootFilter != null) {
      return rootFilter.getRoots().contains(root);
    }
    return true;
  }

  private void setVisible(@NotNull VirtualFile root, boolean visible) {
    Set<VirtualFile> roots = getAllRoots();

    VcsLogRootFilter rootFilter = getRootFilter(myFilterModel.getFilter());

    Collection<VirtualFile> visibleRoots;
    if (rootFilter == null) {
      visibleRoots = visible ? roots : ContainerUtil.subtract(roots, Collections.singleton(root));
    }
    else {
      visibleRoots = visible
                     ? ContainerUtil.union(new HashSet<>(rootFilter.getRoots()), Collections.singleton(root))
                     : ContainerUtil.subtract(rootFilter.getRoots(), Collections.singleton(root));
    }
    myFilterModel.setFilter(new FilterPair<>(null, VcsLogFilterObject.fromRoots(visibleRoots)));
  }

  private void setVisibleOnly(@NotNull VirtualFile root) {
    myFilterModel.setFilter(new FilterPair<>(null, VcsLogFilterObject.fromRoot(root)));
  }

  @NotNull
  private static String getStructureActionText(@NotNull VcsLogStructureFilter filter) {
    return getTextFromFilePaths(filter.getFiles(), "items", filter.getFiles().isEmpty());
  }

  private static class FileByNameComparator implements Comparator<VirtualFile> {
    @Override
    public int compare(VirtualFile o1, VirtualFile o2) {
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
      myIcon = JBUI.scale(new CheckboxColorIcon(CHECKBOX_ICON_SIZE, VcsLogGraphTable.getRootBackgroundColor(myRoot, myColorManager)));
      getTemplatePresentation().setIcon(JBUI.scale(EmptyIcon.create(CHECKBOX_ICON_SIZE))); // see PopupFactoryImpl.calcMaxIconSize
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isVisible(myRoot);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
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
      return getStructureFilter(myFilterModel.getFilter()) == null;
    }
  }

  private static class CheckboxColorIcon extends ColorIcon {
    private boolean mySelected;
    private SizedIcon mySizedIcon;

    CheckboxColorIcon(int size, @NotNull Color color) {
      super(size, color);
      mySizedIcon = new SizedIcon(PlatformIcons.CHECK_ICON_SMALL, size, size);
    }

    public void prepare(boolean selected) {
      mySelected = selected;
    }

    @NotNull
    @Override
    public CheckboxColorIcon withIconPreScaled(boolean preScaled) {
      mySizedIcon = (SizedIcon)mySizedIcon.withIconPreScaled(preScaled);
      return (CheckboxColorIcon)super.withIconPreScaled(preScaled);
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
    static final String STRUCTURE_FILTER_TEXT = "Select Folders...";

    SelectFoldersAction() {
      super(STRUCTURE_FILTER_TEXT);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      VcsLogDataPack dataPack = myFilterModel.getDataPack();
      VcsLogStructureFilter structureFilter = getStructureFilter(myFilterModel.getFilter());

      Collection<VirtualFile> files;
      if (structureFilter == null) {
        files = Collections.emptySet();
      }
      else {
        // for now, ignoring non-existing paths
        files = ContainerUtil.mapNotNull(structureFilter.getFiles(), FilePath::getVirtualFile);
      }

      VcsStructureChooser chooser = new VcsStructureChooser(project, "Select Files or Folders to Filter by", files,
                                                            new ArrayList<>(dataPack.getLogProviders().keySet()));
      if (chooser.showAndGet()) {
        VcsLogStructureFilter newFilter = VcsLogFilterObject.fromVirtualFiles(chooser.getSelectedFiles());
        myFilterModel.setFilter(new FilterPair<>(newFilter, null));
        myUiProperties.addRecentlyFilteredGroup(myName, VcsLogClassicFilterUi.FileFilterModel.getFilterValues(newFilter));
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
  }

  private class SelectFromHistoryAction extends ToggleAction implements DumbAware {
    @NotNull private final VcsLogStructureFilter myFilter;
    @NotNull private final Icon myIcon;
    @NotNull private final Icon myEmptyIcon;

    private SelectFromHistoryAction(@NotNull VcsLogStructureFilter filter) {
      super(getStructureActionText(filter), getTooltipTextForFilePaths(filter.getFiles()).replace("\n", " "), null);
      myFilter = filter;
      myIcon = JBUI.scale(new SizedIcon(PlatformIcons.CHECK_ICON_SMALL, CHECKBOX_ICON_SIZE, CHECKBOX_ICON_SIZE));
      myEmptyIcon = JBUI.scale(EmptyIcon.create(CHECKBOX_ICON_SIZE));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return getStructureFilter(myFilterModel.getFilter()) == myFilter;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myFilterModel.setFilter(new FilterPair<>(myFilter, null));
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
}

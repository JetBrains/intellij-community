// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.popup.KeepingPopupOpenAction;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcsUtil.VcsUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.*;

public class StructureFilterPopupComponent
  extends FilterPopupComponent<FilterPair<VcsLogStructureFilter, VcsLogRootFilter>, VcsLogClassicFilterUi.FileFilterModel> {
  private static final String PATHS = "Paths";
  private static final int FILTER_LABEL_LENGTH = 30;
  private static final int CHECKBOX_ICON_SIZE = 15;
  private static final FileByNameComparator FILE_BY_NAME_COMPARATOR = new FileByNameComparator();
  private static final FilePathByPathComparator FILE_PATH_BY_PATH_COMPARATOR = new FilePathByPathComparator();

  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogColorManager myColorManager;

  public StructureFilterPopupComponent(@NotNull MainVcsLogUiProperties uiProperties,
                                       @NotNull VcsLogClassicFilterUi.FileFilterModel filterModel,
                                       @NotNull VcsLogColorManager colorManager) {
    super(VcsLogBundle.messagePointer("vcs.log.filter.popup.paths"), filterModel);
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
  @Nls
  protected String getText(@NotNull FilterPair<VcsLogStructureFilter, VcsLogRootFilter> filter) {
    VcsLogRootFilter rootFilter = getRootFilter(filter);
    VcsLogStructureFilter structureFilter = getStructureFilter(filter);
    Collection<VirtualFile> visibleRoots = VcsLogUtil.getAllVisibleRoots(getAllRoots(), rootFilter, structureFilter);

    Collection<VirtualFile> roots = getFilterRoots(rootFilter);
    Collection<FilePath> files = getFilterFiles(structureFilter);
    if (files.isEmpty()) {
      return getTextFromRoots(roots, visibleRoots.size() == getAllRoots().size());
    }
    return getTextFromFilePaths(files, VcsLogBundle.message("vcs.log.filter.popup.no.folders"), false);
  }

  @NotNull
  @Nls
  private static String getTextFromRoots(@NotNull Collection<? extends VirtualFile> files,
                                         boolean full) {
    return getText(files, VcsLogBundle.message("vcs.log.filter.popup.no.roots"), FILE_BY_NAME_COMPARATOR, VirtualFile::getName, full);
  }

  @NotNull
  @Nls
  private static String getTextFromFilePaths(@NotNull Collection<? extends FilePath> files,
                                             @Nls @NotNull String categoryText, boolean full) {
    return getText(files, categoryText, FILE_PATH_BY_PATH_COMPARATOR,
                   file -> StringUtil.shortenPathWithEllipsis(path2Text(file), FILTER_LABEL_LENGTH), full);
  }

  @NotNull
  @Nls
  private static <F> String getText(@NotNull Collection<? extends F> files,
                                    @Nls @NotNull String categoryText,
                                    @NotNull Comparator<? super F> comparator,
                                    @NotNull NotNullFunction<? super F, String> getText,
                                    boolean full) {
    if (full) {
      return ALL.get();
    }
    else if (files.isEmpty()) {
      return categoryText;
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

  @Nls
  @Override
  protected String getToolTip(@NotNull FilterPair<VcsLogStructureFilter, VcsLogRootFilter> filter) {
    return getToolTip(getFilterRoots(getRootFilter(filter)), getFilterFiles(getStructureFilter(filter)));
  }

  @Nls
  @NotNull
  private String getToolTip(@NotNull Collection<? extends VirtualFile> roots, @NotNull Collection<? extends FilePath> files) {
    String tooltip = "";
    if (roots.isEmpty()) {
      tooltip += VcsLogBundle.message("vcs.log.filter.tooltip.no.roots.selected");
    }
    else if (roots.size() != getAllRoots().size()) {
      tooltip += VcsLogBundle.message("vcs.log.filter.tooltip.roots") + UIUtil.BR + getTooltipTextForRoots(roots);
    }

    if (!files.isEmpty()) {
      if (!tooltip.isEmpty()) tooltip += UIUtil.BR;
      tooltip += VcsLogBundle.message("vcs.log.filter.tooltip.folders") + UIUtil.BR +  getTooltipTextForFilePaths(files);
    }

    return tooltip;
  }

  @NotNull
  private static String getTooltipTextForRoots(@NotNull Collection<? extends VirtualFile> files) {
    return getTooltipTextForFiles(files, FILE_BY_NAME_COMPARATOR, VirtualFile::getName);
  }

  @NotNull
  private static String getTooltipTextForFilePaths(@NotNull Collection<? extends FilePath> files) {
    return getTooltipTextForFiles(files, FILE_PATH_BY_PATH_COMPARATOR, StructureFilterPopupComponent::path2Text);
  }

  @NotNull
  private static <F> String getTooltipTextForFiles(@NotNull Collection<? extends F> files,
                                                   @NotNull Comparator<? super F> comparator,
                                                   @NotNull NotNullFunction<? super F, String> getText) {
    List<F> filesToDisplay = ContainerUtil.sorted(files, comparator);
    filesToDisplay = ContainerUtil.getFirstItems(filesToDisplay, 10);
    String tooltip = StringUtil.join(filesToDisplay, getText, UIUtil.BR);
    if (files.size() > 10) {
      tooltip += UIUtil.BR + "...";
    }
    return tooltip;
  }

  @Override
  protected ActionGroup createActionGroup() {
    Set<VirtualFile> roots = getAllRoots();

    List<AnAction> rootActions = new ArrayList<>();
    if (myColorManager.hasMultiplePaths()) {
      for (VirtualFile root : ContainerUtil.sorted(roots, FILE_BY_NAME_COMPARATOR)) {
        rootActions.add(new SelectVisibleRootAction(root));
      }
    }
    List<AnAction> structureActions = new ArrayList<>();
    for (VcsLogStructureFilter filter : getRecentFilters()) {
      structureActions.add(new SelectFromHistoryAction(filter));
    }

    if (roots.size() > 15) {
      return new DefaultActionGroup(createAllAction(), new EditPathsAction(), new SelectPathsInTreeAction(),
                                    new Separator(VcsLogBundle.messagePointer("vcs.log.filter.recent")),
                                    new DefaultActionGroup(structureActions),
                                    new Separator(VcsLogBundle.messagePointer("vcs.log.filter.roots")),
                                    new DefaultActionGroup(rootActions));
    }
    return new DefaultActionGroup(createAllAction(), new EditPathsAction(), new SelectPathsInTreeAction(),
                                  new Separator(VcsLogBundle.messagePointer("vcs.log.filter.roots")),
                                  new DefaultActionGroup(rootActions),
                                  new Separator(VcsLogBundle.messagePointer("vcs.log.filter.recent")),
                                  new DefaultActionGroup(structureActions));
  }

  @NotNull
  private List<VcsLogStructureFilter> getRecentFilters() {
    List<List<String>> filterValues = myUiProperties.getRecentlyFilteredGroups(PATHS);
    return ContainerUtil.map2List(filterValues, values -> VcsLogClassicFilterUi.FileFilterModel.createStructureFilter(values));
  }

  private Set<VirtualFile> getAllRoots() {
    return myFilterModel.getRoots();
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
    return getTextFromFilePaths(filter.getFiles(), VcsLogBundle.message("vcs.log.filter.popup.no.items"), filter.getFiles().isEmpty());
  }

  @NotNull
  private static String path2Text(@NotNull FilePath filePath) {
    return filePath.getPresentableUrl() + (filePath.isDirectory() ? File.separator : "");
  }

  @NotNull
  private static FilePath text2Path(@NotNull String path) {
    if (path.endsWith(File.separator)) {
      return VcsUtil.getFilePath(path, true);
    }
    return VcsUtil.getFilePath(path);
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
      super(null, root.getPresentableUrl(), null);
      getTemplatePresentation().setText(root.getName(), false);
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
      String tooltip = VcsLogBundle.message("vcs.log.filter.tooltip.click.to.see.only",
                                            KeyEvent.getKeyModifiersText(getMask()),
                                            e.getPresentation().getText());
      e.getPresentation().putClientProperty(TOOL_TIP_TEXT_KEY, tooltip);
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

  private class SelectPathsInTreeAction extends DumbAwareAction {

    SelectPathsInTreeAction() {
      super(VcsLogBundle.messagePointer("vcs.log.filter.select.folders"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      Set<VirtualFile> roots = myFilterModel.getRoots();

      // for now, ignoring non-existing paths
      Collection<VirtualFile> files = ContainerUtil.mapNotNull(getStructureFilterPaths(), FilePath::getVirtualFile);

      VcsStructureChooser chooser = new VcsStructureChooser(project, VcsLogBundle.message("vcs.log.select.folder.dialog.title"), files,
                                                            new ArrayList<>(roots));
      if (chooser.showAndGet()) {
        VcsLogStructureFilter newFilter = VcsLogFilterObject.fromVirtualFiles(chooser.getSelectedFiles());
        setStructureFilter(newFilter);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
  }

  private class EditPathsAction extends DumbAwareAction {
    EditPathsAction() {
      super(VcsLogBundle.messagePointer("vcs.log.filter.edit.folders"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);

      Collection<FilePath> filesPaths = ContainerUtil.sorted(getStructureFilterPaths(), HierarchicalFilePathComparator.NATURAL);

      String oldValue = StringUtil.join(ContainerUtil.map(filesPaths, StructureFilterPopupComponent::path2Text), "\n");
      MultilinePopupBuilder popupBuilder = new MultilinePopupBuilder(project, Collections.emptyList(), oldValue, null);

      JBPopup popup = popupBuilder.createPopup();
      popup.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (event.isOk()) {
            List<FilePath> selectedPaths = ContainerUtil.map(popupBuilder.getSelectedValues(), StructureFilterPopupComponent::text2Path);
            setStructureFilter(VcsLogFilterObject.fromPaths(selectedPaths));
          }
        }
      });
      popup.showUnderneathOf(StructureFilterPopupComponent.this);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
  }

  @NotNull
  private Collection<FilePath> getStructureFilterPaths() {
    VcsLogStructureFilter structureFilter = getStructureFilter(myFilterModel.getFilter());
    return structureFilter != null ? structureFilter.getFiles() : Collections.emptyList();
  }

  private void setStructureFilter(@NotNull VcsLogStructureFilter newFilter) {
    myFilterModel.setFilter(new FilterPair<>(newFilter, null));
    myUiProperties.addRecentlyFilteredGroup(PATHS, VcsLogClassicFilterUi.FileFilterModel.getFilterValues(newFilter));
  }

  private class SelectFromHistoryAction extends ToggleAction implements DumbAware {
    @NotNull private final VcsLogStructureFilter myFilter;
    @NotNull private final Icon myIcon;
    @NotNull private final Icon myEmptyIcon;

    private SelectFromHistoryAction(@NotNull VcsLogStructureFilter filter) {
      super(getStructureActionText(filter), getTooltipTextForFilePaths(filter.getFiles()).replace(UIUtil.BR, " "), null);
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

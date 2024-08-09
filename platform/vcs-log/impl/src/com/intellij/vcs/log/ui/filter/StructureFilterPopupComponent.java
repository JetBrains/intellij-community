// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.HierarchicalFilePathComparator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CheckboxIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.*;
import java.util.function.Function;

public class StructureFilterPopupComponent extends FilterPopupComponent<VcsLogFilterCollection, FileFilterModel> {
  private static final String PATHS = "Paths";
  private static final int FILTER_LABEL_LENGTH = 30;
  private static final int CHECKBOX_ICON_SIZE = 15;
  private static final FileByNameComparator FILE_BY_NAME_COMPARATOR = new FileByNameComparator();
  private static final FilePathByPathComparator FILE_PATH_BY_PATH_COMPARATOR = new FilePathByPathComparator();

  private final @NotNull MainVcsLogUiProperties myUiProperties;
  private final @NotNull VcsLogColorManager myColorManager;

  public StructureFilterPopupComponent(@NotNull MainVcsLogUiProperties uiProperties,
                                       @NotNull FileFilterModel filterModel,
                                       @NotNull VcsLogColorManager colorManager) {
    super(VcsLogBundle.messagePointer("vcs.log.filter.popup.paths"), filterModel);
    myUiProperties = uiProperties;
    myColorManager = colorManager;
  }

  private static VcsLogRootFilter getRootFilter(@Nullable VcsLogFilterCollection filter) {
    if (filter == null) return null;
    return filter.get(VcsLogFilterCollection.ROOT_FILTER);
  }

  private static VcsLogStructureFilter getStructureFilter(@Nullable VcsLogFilterCollection filter) {
    if (filter == null) return null;
    return filter.get(VcsLogFilterCollection.STRUCTURE_FILTER);
  }

  private @NotNull Collection<VirtualFile> getFilterRoots(@Nullable VcsLogRootFilter filter) {
    return filter != null ? filter.getRoots() : getAllRoots();
  }

  private static @NotNull Collection<FilePath> getFilterFiles(@Nullable VcsLogStructureFilter filter) {
    return filter != null ? filter.getFiles() : Collections.emptySet();
  }

  @Override
  protected @NotNull @Nls String getText(@NotNull VcsLogFilterCollection filter) {
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

  private static @NotNull @Nls String getTextFromRoots(@NotNull Collection<? extends VirtualFile> files,
                                                       boolean full) {
    return getText(files, VcsLogBundle.message("vcs.log.filter.popup.no.roots"), FILE_BY_NAME_COMPARATOR, VirtualFile::getName, full);
  }

  private @NotNull @Nls String getTextFromFilePaths(@NotNull Collection<? extends FilePath> files,
                                                    @Nls @NotNull String categoryText, boolean full) {
    return getText(files, categoryText, FILE_PATH_BY_PATH_COMPARATOR,
                   file -> StringUtil.shortenPathWithEllipsis(path2Text(file, true), FILTER_LABEL_LENGTH), full);
  }

  private static @NotNull @Nls <F> String getText(@NotNull Collection<? extends F> files,
                                                  @Nls @NotNull String categoryText,
                                                  @NotNull Comparator<? super F> comparator,
                                                  @NotNull NotNullFunction<? super F, @Nls String> getText,
                                                  boolean full) {
    if (full) {
      return ALL_ACTION_TEXT.get();
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

  @Override
  protected @Nls String getToolTip(@NotNull VcsLogFilterCollection filter) {
    return getToolTip(getFilterRoots(getRootFilter(filter)), getFilterFiles(getStructureFilter(filter)));
  }

  private @Nls @NotNull String getToolTip(@NotNull Collection<? extends VirtualFile> roots, @NotNull Collection<? extends FilePath> files) {
    HtmlBuilder tooltip = new HtmlBuilder();
    if (roots.isEmpty()) {
      tooltip.append(VcsLogBundle.message("vcs.log.filter.tooltip.no.roots.selected"));
    }
    else if (roots.size() != getAllRoots().size()) {
      tooltip.append(VcsLogBundle.message("vcs.log.filter.tooltip.roots")).br().append(getTooltipTextForRoots(roots));
    }

    if (!files.isEmpty()) {
      if (!tooltip.isEmpty()) tooltip.br();
      tooltip.append(VcsLogBundle.message("vcs.log.filter.tooltip.folders")).br().append(getTooltipTextForFilePaths(files, HtmlChunk.br()));
    }

    return tooltip.toString();
  }

  private static @NotNull @NlsContexts.Tooltip HtmlChunk getTooltipTextForRoots(@NotNull Collection<? extends VirtualFile> files) {
    return getTooltipTextForFiles(files, FILE_BY_NAME_COMPARATOR, VirtualFile::getName, HtmlChunk.br());
  }

  private @NotNull @NlsContexts.Tooltip HtmlChunk getTooltipTextForFilePaths(@NotNull Collection<? extends FilePath> files,
                                                                             @NotNull HtmlChunk separator) {
    return getTooltipTextForFiles(files, FILE_PATH_BY_PATH_COMPARATOR, filePath -> path2Text(filePath, true), separator);
  }

  private static @NotNull @NlsContexts.Tooltip <F> HtmlChunk getTooltipTextForFiles(@NotNull Collection<? extends F> files,
                                                                                    @NotNull Comparator<? super F> comparator,
                                                                                    @NotNull Function<? super F, @NotNull @Nls String> getText,
                                                                                    @NotNull HtmlChunk separator) {
    List<F> filesToDisplay = ContainerUtil.sorted(files, comparator);
    filesToDisplay = ContainerUtil.getFirstItems(filesToDisplay, 10);
    HtmlBuilder tooltip = new HtmlBuilder().appendWithSeparators(separator,
                                                                 ContainerUtil.map(filesToDisplay, f -> HtmlChunk.text(getText.apply(f))));
    if (files.size() > 10) {
      tooltip.append(separator).append("...");
    }
    return tooltip.toFragment();
  }

  @Override
  protected @NotNull ActionGroup createActionGroup() {
    Set<VirtualFile> roots = getAllRoots();

    List<SelectVisibleRootAction> rootActions = new ArrayList<>();
    if (myColorManager.hasMultiplePaths()) {
      for (VirtualFile root : ContainerUtil.sorted(roots, FILE_BY_NAME_COMPARATOR)) {
        rootActions.add(new SelectVisibleRootAction(root, rootActions));
      }
    }
    List<AnAction> structureActions = new ArrayList<>();
    for (VcsLogStructureFilter filter : getRecentFilters()) {
      structureActions.add(new SelectFromHistoryAction(filter));
    }

    List<AnAction> actionsList = new ArrayList<>(Arrays.asList(new EditPathsAction(), new SelectPathsInTreeAction(),
                                                               new Separator(VcsLogBundle.messagePointer("vcs.log.filter.recent")),
                                                               new DefaultActionGroup(structureActions)));

    int position = roots.size() > 15 ? actionsList.size() : actionsList.size() - 2;
    actionsList.addAll(position, List.of(new Separator(VcsLogBundle.messagePointer("vcs.log.filter.roots")),
                                         new DefaultActionGroup(rootActions)));
    return new DefaultActionGroup(actionsList);
  }

  private @NotNull List<VcsLogStructureFilter> getRecentFilters() {
    List<List<String>> filterValues = myUiProperties.getRecentlyFilteredGroups(PATHS);
    return ContainerUtil.map(filterValues, values -> FileFilterModel.createStructureFilter(values));
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
    myFilterModel.setFilter(VcsLogFilterObject.collection(VcsLogFilterObject.fromRoots(visibleRoots)));
  }

  private void setVisibleOnly(@NotNull VirtualFile root) {
    myFilterModel.setFilter(VcsLogFilterObject.collection(VcsLogFilterObject.fromRoot(root)));
  }

  private @NotNull @NlsActions.ActionText String getStructureActionText(@NotNull VcsLogStructureFilter filter) {
    return getTextFromFilePaths(filter.getFiles(), VcsLogBundle.message("vcs.log.filter.popup.no.items"), filter.getFiles().isEmpty());
  }

  private @NotNull @NlsSafe String path2Text(@NotNull FilePath filePath, boolean systemDependent) {
    return path2Text(filePath, systemDependent, getAllRoots());
  }

  @NotNull
  @NlsSafe
  public static String path2Text(@NotNull FilePath filePath, boolean systemDependent, Set<VirtualFile> roots) {
    VirtualFile commonAncestor = VfsUtil.getCommonAncestor(roots);
    String path = null;
    if (commonAncestor != null) {
      path = FileUtil.getRelativePath(commonAncestor.getPath(), filePath.getPath(), '/');
      if (path != null && systemDependent) path = FileUtil.toSystemDependentName(path);
    }
    if (path == null) path = systemDependent ? filePath.getPresentableUrl() : filePath.getPath();
    char separator = separator(systemDependent);
    return path + (filePath.isDirectory() && !StringUtil.endsWithChar(path, separator) ? separator : "");
  }

  private @NotNull FilePath text2Path(@NotNull String path) {
    path = FileUtil.toSystemIndependentName(path);
    boolean isDirectory = StringUtil.endsWithChar(path, '/');

    VirtualFile commonAncestor = VfsUtil.getCommonAncestor(getAllRoots());
    if (commonAncestor != null && !FileUtil.isAbsolute(FileUtil.toSystemDependentName(path))) {
      path = commonAncestor.getPath() + '/' + path;
    }

    if (isDirectory) {
      return VcsUtil.getFilePath(path, true);
    }
    return VcsUtil.getFilePath(path);
  }

  private static char separator(boolean systemDependent) {
    return systemDependent ? File.separatorChar : '/';
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

  private final class SelectVisibleRootAction extends DumbAwareToggleAction {
    final CheckboxIcon.WithColor myIcon;
    final VirtualFile myRoot;
    final List<SelectVisibleRootAction> myAllActions;

    SelectVisibleRootAction(@NotNull VirtualFile root, @NotNull List<SelectVisibleRootAction> allActions) {
      super(null, root.getPresentableUrl(), null);
      getTemplatePresentation().setText(root.getName(), false);
      myRoot = root;
      myAllActions = allActions;
      myIcon = CheckboxIcon.createAndScaleCheckbox(myColorManager.getRootColor(myRoot));
      getTemplatePresentation().setIcon(JBUIScale.scaleIcon(EmptyIcon.create(CHECKBOX_ICON_SIZE))); // see PopupFactoryImpl.calcMaxIconSize
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
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
      else if ((e.getModifiers() & getModifier()) != 0) {
        setVisibleOnly(myRoot);
      }
      else {
        setVisible(myRoot, state);
      }
      for (SelectVisibleRootAction action : myAllActions) {
        action.updateIcon();
      }
    }

    private static int getModifier() {
      return ClientSystemInfo.isMac() ? ActionEvent.META_MASK : ActionEvent.CTRL_MASK;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);

      updateIcon();
      e.getPresentation().setIcon(myIcon);
      var modifierText = InputEvent.getModifiersExText(getModifier() << 6);
      var tooltip = VcsLogBundle.message("vcs.log.filter.tooltip.click.to.see.only", modifierText, e.getPresentation().getText());
      e.getPresentation().putClientProperty(ActionUtil.TOOLTIP_TEXT, tooltip);
    }

    private void updateIcon() {
      myIcon.prepare(isVisible(myRoot) && isEnabled());
    }

    private boolean isEnabled() {
      return getStructureFilter(myFilterModel.getFilter()) == null;
    }
  }

  private class SelectPathsInTreeAction extends DumbAwareAction {

    SelectPathsInTreeAction() {
      super(VcsLogBundle.messagePointer("vcs.log.filter.select.folders"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getData(CommonDataKeys.PROJECT);
      if (project == null) return;
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
      Project project = e.getData(CommonDataKeys.PROJECT);
      if (project == null) return;

      Collection<FilePath> filesPaths = ContainerUtil.sorted(getStructureFilterPaths(), HierarchicalFilePathComparator.NATURAL);

      String oldValue = StringUtil.join(ContainerUtil.map(filesPaths, filePath -> path2Text(filePath, false)), "\n");
      MultilinePopupBuilder popupBuilder = new MultilinePopupBuilder(project, oldValue, new char[]{'\n'});

      JBPopup popup = popupBuilder.createPopup();
      popup.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (event.isOk()) {
            List<FilePath> selectedPaths = ContainerUtil.map(popupBuilder.getSelectedValues(), path -> text2Path(path));
            if (selectedPaths.isEmpty()) {
              myFilterModel.setFilter(null);
            }
            else {
              setStructureFilter(VcsLogFilterObject.fromPaths(selectedPaths));
            }
          }
        }
      });
      popup.showUnderneathOf(getTargetComponent());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
  }

  private @NotNull Collection<FilePath> getStructureFilterPaths() {
    VcsLogStructureFilter structureFilter = getStructureFilter(myFilterModel.getFilter());
    return structureFilter != null ? structureFilter.getFiles() : Collections.emptyList();
  }

  private void setStructureFilter(@NotNull VcsLogStructureFilter newFilter) {
    myFilterModel.setFilter(VcsLogFilterObject.collection(newFilter));
    myUiProperties.addRecentlyFilteredGroup(PATHS, FileFilterModel.getStructureFilterValues(newFilter));
  }

  private final class SelectFromHistoryAction extends ToggleAction implements DumbAware {
    private final @NotNull VcsLogStructureFilter myFilter;
    private final @NotNull Icon myIcon;
    private final @NotNull Icon myEmptyIcon;

    private SelectFromHistoryAction(@NotNull VcsLogStructureFilter filter) {
      super(getStructureActionText(filter), getTooltipTextForFilePaths(filter.getFiles(), HtmlChunk.text(" ")).toString(), null);
      myFilter = filter;
      myIcon = JBUIScale.scaleIcon(new SizedIcon(PlatformIcons.CHECK_ICON_SMALL, CHECKBOX_ICON_SIZE, CHECKBOX_ICON_SIZE));
      myEmptyIcon = JBUIScale.scaleIcon(EmptyIcon.create(CHECKBOX_ICON_SIZE));
      getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.Never);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myFilter.equals(getStructureFilter(myFilterModel.getFilter()));
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myFilterModel.setFilter(VcsLogFilterObject.collection(myFilter));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
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

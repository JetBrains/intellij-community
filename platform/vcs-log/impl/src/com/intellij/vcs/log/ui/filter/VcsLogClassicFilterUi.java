// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFileFilter;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcs.log.visible.filters.VcsLogUserFilterImpl;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.util.*;

/**
 *
 */
public class VcsLogClassicFilterUi implements VcsLogFilterUi {
  private static final String VCS_LOG_TEXT_FILTER_HISTORY = "Vcs.Log.Text.Filter.History";
  private static final Logger LOG = Logger.getInstance(VcsLogClassicFilterUi.class);

  @NotNull private final VcsLogUiImpl myUi;

  @NotNull private final VcsLogData myLogData;
  @NotNull private final MainVcsLogUiProperties myUiProperties;

  @NotNull private VcsLogDataPack myDataPack;

  @NotNull private final BranchFilterModel myBranchFilterModel;
  @NotNull private final FilterModel<VcsLogUserFilter> myUserFilterModel;
  @NotNull private final FilterModel<VcsLogDateFilter> myDateFilterModel;
  @NotNull private final FilterModel<VcsLogFileFilter> myStructureFilterModel;
  @NotNull private final TextFilterModel myTextFilterModel;

  public VcsLogClassicFilterUi(@NotNull VcsLogUiImpl ui,
                               @NotNull VcsLogData logData,
                               @NotNull MainVcsLogUiProperties uiProperties,
                               @NotNull VcsLogDataPack initialDataPack) {
    myUi = ui;
    myLogData = logData;
    myUiProperties = uiProperties;
    myDataPack = initialDataPack;

    NotNullComputable<VcsLogDataPack> dataPackGetter = () -> myDataPack;
    myBranchFilterModel = new BranchFilterModel(dataPackGetter, myUiProperties);
    myUserFilterModel = new UserFilterModel(dataPackGetter, myUiProperties);
    myDateFilterModel = new DateFilterModel(dataPackGetter, myUiProperties);
    myStructureFilterModel = new FileFilterModel(dataPackGetter, myLogData.getLogProviders().keySet(), myUiProperties);
    myTextFilterModel = new TextFilterModel(dataPackGetter, myUiProperties);

    updateUiOnFilterChange();
    myUi.applyFiltersAndUpdateUi(getFilters());
  }

  private void updateUiOnFilterChange() {
    FilterModel[] models = {myBranchFilterModel, myUserFilterModel, myDateFilterModel, myStructureFilterModel, myTextFilterModel};
    for (FilterModel<?> model : models) {
      model.addSetFilterListener(() -> {
        myUi.applyFiltersAndUpdateUi(getFilters());
        myBranchFilterModel
          .onStructureFilterChanged(new HashSet<>(myLogData.getRoots()), myStructureFilterModel.getFilter());
      });
    }
  }

  public void updateDataPack(@NotNull VcsLogDataPack dataPack) {
    myDataPack = dataPack;
  }

  @NotNull
  public SearchTextField createTextFilter() {
    return new TextFilterField(myTextFilterModel);
  }

  /**
   * Returns filter components which will be added to the Log toolbar.
   */
  @NotNull
  public ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new FilterActionComponent(() -> new BranchFilterPopupComponent(myUiProperties, myBranchFilterModel).initUi()));
    actionGroup.add(new FilterActionComponent(() -> new UserFilterPopupComponent(myUiProperties, myLogData, myUserFilterModel).initUi()));
    actionGroup.add(new FilterActionComponent(() -> new DateFilterPopupComponent(myDateFilterModel).initUi()));
    actionGroup.add(new FilterActionComponent(
      () -> new StructureFilterPopupComponent(myUiProperties, myStructureFilterModel, myUi.getColorManager()).initUi()));
    return actionGroup;
  }

  @NotNull
  @Override
  public VcsLogFilterCollection getFilters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Pair<VcsLogTextFilter, VcsLogHashFilter> filtersFromText =
      getFiltersFromTextArea(myTextFilterModel.getFilter(),
                             myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_REGEX),
                             myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE));
    return VcsLogFilterObject.collection(myBranchFilterModel.getFilter(), myUserFilterModel.getFilter(), filtersFromText.second,
                                         myDateFilterModel.getFilter(), filtersFromText.first,
                                         myStructureFilterModel.getFilter() == null
                                         ? null
                                         : myStructureFilterModel.getFilter().getStructureFilter(),
                                         myStructureFilterModel.getFilter() == null
                                         ? null
                                         : myStructureFilterModel.getFilter().getRootFilter());
  }

  @NotNull
  private static Pair<VcsLogTextFilter, VcsLogHashFilter> getFiltersFromTextArea(@Nullable VcsLogTextFilter filter,
                                                                                 boolean isRegexAllowed,
                                                                                 boolean matchesCase) {
    if (filter == null) {
      return Pair.empty();
    }
    String text = filter.getText().trim();
    if (StringUtil.isEmptyOrSpaces(text)) {
      return Pair.empty();
    }

    VcsLogTextFilter textFilter = VcsLogFilterObject.fromPattern(text, isRegexAllowed, matchesCase);
    VcsLogHashFilter hashFilter = VcsLogFilterObject.fromHash(text);
    return Pair.create(textFilter, hashFilter);
  }

  /**
   * Only VcsLogBranchFilter, VcsLogStructureFilter and null (which means resetting all filters) are currently supported.
   */
  @Override
  public void setFilter(@Nullable VcsLogFilter filter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (filter == null) {
      myBranchFilterModel.setFilter(null);
      myStructureFilterModel.setFilter(null);
      myDateFilterModel.setFilter(null);
      myTextFilterModel.setFilter(null);
      myUserFilterModel.setFilter(null);
    }
    else if (filter instanceof VcsLogBranchFilter) {
      myBranchFilterModel.setFilter((VcsLogBranchFilter)filter);
    }
    else if (filter instanceof VcsLogStructureFilter) {
      myStructureFilterModel.setFilter(new VcsLogFileFilter((VcsLogStructureFilter)filter, null));
    }

    JComponent toolbar = myUi.getToolbar();
    toolbar.revalidate();
    toolbar.repaint();
  }

  private static class FilterActionComponent extends DumbAwareAction implements CustomComponentAction {

    @NotNull private final Computable<? extends JComponent> myComponentCreator;

    FilterActionComponent(@NotNull Computable<? extends JComponent> componentCreator) {
      myComponentCreator = componentCreator;
    }

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation) {
      return myComponentCreator.compute();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }

  public static class BranchFilterModel extends FilterModel<VcsLogBranchFilter> {
    @Nullable
    private Collection<VirtualFile> myVisibleRoots;

    BranchFilterModel(@NotNull Computable<? extends VcsLogDataPack> provider, @NotNull MainVcsLogUiProperties properties) {
      super("branch", provider, properties);
    }

    public void onStructureFilterChanged(@NotNull Set<VirtualFile> roots, @Nullable VcsLogFileFilter filter) {
      if (filter == null) {
        myVisibleRoots = null;
      }
      else {
        myVisibleRoots = VcsLogUtil.getAllVisibleRoots(roots, filter.getRootFilter(), filter.getStructureFilter());
      }
    }

    @Nullable
    public Collection<VirtualFile> getVisibleRoots() {
      return myVisibleRoots;
    }

    @NotNull
    @Override
    protected VcsLogBranchFilter createFilter(@NotNull List<String> values) {
      return VcsLogFilterObject.fromBranchPatterns(values,
                                                   ContainerUtil.map2Set(getDataPack().getRefs().getBranches(), VcsRef::getName));
    }

    @NotNull
    @Override
    protected List<String> getFilterValues(@NotNull VcsLogBranchFilter filter) {
      return ContainerUtil.newArrayList(ContainerUtil.sorted(filter.getTextPresentation()));
    }
  }

  private static class TextFilterModel extends FilterModel<VcsLogTextFilter> {
    @Nullable private String myText;

    TextFilterModel(NotNullComputable<? extends VcsLogDataPack> dataPackProvider, @NotNull MainVcsLogUiProperties properties) {
      super("text", dataPackProvider, properties);
    }

    @NotNull
    String getText() {
      if (myText != null) {
        return myText;
      }
      else if (getFilter() != null) {
        return getFilter().getText();
      }
      else {
        return "";
      }
    }

    void setUnsavedText(@NotNull String text) {
      myText = text;
    }

    boolean hasUnsavedChanges() {
      if (myText == null) return false;
      return getFilter() == null || !myText.equals(getFilter().getText());
    }

    @Override
    void setFilter(@Nullable VcsLogTextFilter filter) {
      super.setFilter(filter);
      myText = null;
    }

    @NotNull
    @Override
    protected VcsLogTextFilter createFilter(@NotNull List<String> values) {
      return VcsLogFilterObject.fromPattern(ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(values)),
                                            myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_REGEX),
                                            myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE));
    }

    @NotNull
    @Override
    protected List<String> getFilterValues(@NotNull VcsLogTextFilter filter) {
      return Collections.singletonList(filter.getText());
    }
  }

  static class FileFilterModel extends FilterModel<VcsLogFileFilter> {
    @NotNull private static final String ROOTS = "roots";
    @NotNull private static final String STRUCTURE = "structure";
    @NotNull private static final String DIR = "dir:";
    @NotNull private static final String FILE = "file:";
    @NotNull private final Set<VirtualFile> myRoots;

    FileFilterModel(NotNullComputable<VcsLogDataPack> dataPackGetter,
                    @NotNull Set<VirtualFile> roots,
                    MainVcsLogUiProperties uiProperties) {
      super("file", dataPackGetter, uiProperties);
      myRoots = roots;
    }

    @Override
    protected void saveFilter(@Nullable VcsLogFileFilter filter) {
      if (filter == null) {
        myUiProperties.saveFilterValues(ROOTS, null);
        myUiProperties.saveFilterValues(STRUCTURE, null);
      }
      else if (filter.getStructureFilter() != null) {
        myUiProperties.saveFilterValues(STRUCTURE, getFilterValues(filter.getStructureFilter()));
      }
      else if (filter.getRootFilter() != null) {
        myUiProperties.saveFilterValues(ROOTS, getFilterValues(filter.getRootFilter()));
      }
    }

    @NotNull
    static List<String> getFilterValues(@NotNull VcsLogStructureFilter filter) {
      return ContainerUtil.map(filter.getFiles(), path -> (path.isDirectory() ? DIR : FILE) + path.getPath());
    }

    @NotNull
    private static List<String> getFilterValues(@NotNull VcsLogRootFilter filter) {
      return ContainerUtil.map(filter.getRoots(), VirtualFile::getPath);
    }

    @NotNull
    static VcsLogStructureFilter createStructureFilter(@NotNull List<String> values) {
      return VcsLogFilterObject.fromPaths(ContainerUtil.map(values, path -> {
        if (path.startsWith(DIR)) {
          return VcsUtil.getFilePath(path.substring(DIR.length()), true);
        }
        else if (path.startsWith(FILE)) {
          return VcsUtil.getFilePath(path.substring(FILE.length()), false);
        }
        return VcsUtil.getFilePath(path);
      }));
    }

    @Nullable
    @Override
    protected VcsLogFileFilter getLastFilter() {
      List<String> values = myUiProperties.getFilterValues(STRUCTURE);
      if (values != null) {
        return new VcsLogFileFilter(createStructureFilter(values), null);
      }
      values = myUiProperties.getFilterValues(ROOTS);
      if (values != null) {
        return new VcsLogFileFilter(null, createRootsFilter(values));
      }
      return null;
    }

    @Nullable
    private VcsLogRootFilter createRootsFilter(@NotNull List<String> values) {
      List<VirtualFile> selectedRoots = ContainerUtil.newArrayList();
      for (String path : values) {
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
        if (root != null) {
          if (myRoots.contains(root)) {
            selectedRoots.add(root);
          }
          else {
            LOG.warn("Can not find VCS root for filtering " + root);
          }
        }
        else {
          LOG.warn("Can not filter by file that does not exist " + path);
        }
      }
      if (selectedRoots.isEmpty()) return null;
      return VcsLogFilterObject.fromRoots(selectedRoots);
    }

    @NotNull
    @Override
    protected VcsLogFileFilter createFilter(@NotNull List<String> values) {
      throw new UnsupportedOperationException("Can not create file filter from list of strings");
    }

    @NotNull
    @Override
    protected List<String> getFilterValues(@NotNull VcsLogFileFilter filter) {
      throw new UnsupportedOperationException("Can not save file filter to a list of strings");
    }
  }

  private static class DateFilterModel extends FilterModel<VcsLogDateFilter> {
    DateFilterModel(NotNullComputable<? extends VcsLogDataPack> dataPackGetter, MainVcsLogUiProperties uiProperties) {
      super("date", dataPackGetter, uiProperties);
    }

    @Nullable
    @Override
    protected VcsLogDateFilter createFilter(@NotNull List<String> values) {
      if (values.size() != 2) {
        LOG.warn("Can not create date filter from " + values + " before and after dates are required.");
        return null;
      }
      String after = values.get(0);
      String before = values.get(1);
      try {
        return VcsLogFilterObject.fromDates(after.isEmpty() ? 0 : Long.parseLong(after),
                                            before.isEmpty() ? 0 : Long.parseLong(before));
      }
      catch (NumberFormatException e) {
        LOG.warn("Can not create date filter from " + values);
      }
      return null;
    }

    @NotNull
    @Override
    protected List<String> getFilterValues(@NotNull VcsLogDateFilter filter) {
      Date after = filter.getAfter();
      Date before = filter.getBefore();
      return Arrays.asList(after == null ? "" : Long.toString(after.getTime()),
                           before == null ? "" : Long.toString(before.getTime()));
    }
  }

  private class UserFilterModel extends FilterModel<VcsLogUserFilter> {
    UserFilterModel(NotNullComputable<? extends VcsLogDataPack> dataPackGetter, MainVcsLogUiProperties uiProperties) {
      super("user", dataPackGetter, uiProperties);
    }

    @NotNull
    @Override
    protected VcsLogUserFilter createFilter(@NotNull List<String> values) {
      return VcsLogFilterObject.fromUserNames(values, myLogData);
    }

    @NotNull
    @Override
    protected List<String> getFilterValues(@NotNull VcsLogUserFilter filter) {
      return ContainerUtil.newArrayList(((VcsLogUserFilterImpl)filter).getUserNamesForPresentation());
    }
  }

  private static class TextFilterField extends SearchTextField {
    @NotNull private final TextFilterModel myTextFilterModel;

    TextFilterField(@NotNull TextFilterModel model) {
      super(VCS_LOG_TEXT_FILTER_HISTORY);
      myTextFilterModel = model;
      setText(myTextFilterModel.getText());
      getTextEditor().addActionListener(e -> applyFilter());
      addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          try {
            myTextFilterModel.setUnsavedText(e.getDocument().getText(0, e.getDocument().getLength()));
          }
          catch (BadLocationException ex) {
            LOG.error(ex);
          }
        }
      });
      String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(VcsLogActionPlaces.VCS_LOG_FOCUS_TEXT_FILTER);
      if (!shortcutText.isEmpty()) {
        getTextEditor().setToolTipText("Use " + shortcutText + " to switch between text filter and commits list");
      }
    }

    protected void applyFilter() {
      boolean isRegexpAllowed = myTextFilterModel.myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_REGEX);
      boolean isMatchCase = myTextFilterModel.myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE);
      myTextFilterModel.setFilter(VcsLogFilterObject.fromPattern(getText(), isRegexpAllowed, isMatchCase));
      addCurrentTextToHistory();
    }

    @Override
    protected void onFieldCleared() {
      myTextFilterModel.setFilter(null);
    }

    @Override
    protected void onFocusLost() {
      if (myTextFilterModel.hasUnsavedChanges()) {
        applyFilter();
      }
    }
  }
}

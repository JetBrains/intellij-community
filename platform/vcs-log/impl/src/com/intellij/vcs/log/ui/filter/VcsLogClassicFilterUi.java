// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.vcs.log.VcsLogFilterCollection.*;
import static java.util.Collections.emptyList;

/**
 *
 */
public class VcsLogClassicFilterUi implements VcsLogFilterUiEx {
  private static final String VCS_LOG_TEXT_FILTER_HISTORY = "Vcs.Log.Text.Filter.History";
  private static final Logger LOG = Logger.getInstance(VcsLogClassicFilterUi.class);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private VcsLogDataPack myDataPack;

  @NotNull protected final BranchFilterModel myBranchFilterModel;
  @NotNull protected final FilterModel.SingleFilterModel<VcsLogUserFilter> myUserFilterModel;
  @NotNull protected final FilterModel<VcsLogDateFilter> myDateFilterModel;
  @NotNull protected final FileFilterModel myStructureFilterModel;
  @NotNull protected final TextFilterModel myTextFilterModel;
  @NotNull private final TextFilterField myFilterField;

  public VcsLogClassicFilterUi(@NotNull VcsLogData logData,
                               @NotNull Consumer<VcsLogFilterCollection> filterConsumer,
                               @NotNull MainVcsLogUiProperties uiProperties,
                               @NotNull VcsLogColorManager colorManager,
                               @Nullable VcsLogFilterCollection filters,
                               @NotNull Disposable parentDisposable) {
    myLogData = logData;
    myUiProperties = uiProperties;
    myDataPack = VisiblePack.EMPTY;
    myColorManager = colorManager;

    NotNullComputable<VcsLogDataPack> dataPackGetter = () -> myDataPack;
    myBranchFilterModel = new BranchFilterModel(dataPackGetter, myLogData.getStorage(), myLogData.getRoots(), myUiProperties, filters);
    myUserFilterModel = new UserFilterModel(myUiProperties, filters);
    myDateFilterModel = new DateFilterModel(myUiProperties, filters);
    myStructureFilterModel = new FileFilterModel(myLogData.getLogProviders().keySet(), myUiProperties, filters);
    myTextFilterModel = new TextFilterModel(myUiProperties, filters, parentDisposable);

    myFilterField = new TextFilterField(myTextFilterModel);

    FilterModel[] models = {myBranchFilterModel, myUserFilterModel, myDateFilterModel, myStructureFilterModel, myTextFilterModel};
    for (FilterModel<?> model : models) {
      model.addSetFilterListener(() -> {
        filterConsumer.consume(getFilters());
        myBranchFilterModel.onStructureFilterChanged(myStructureFilterModel.getRootFilter(), myStructureFilterModel.getStructureFilter());
      });
    }
  }

  @Override
  public void updateDataPack(@NotNull VcsLogDataPack newDataPack) {
    myDataPack = newDataPack;
  }

  @Override
  @NotNull
  public SearchTextField getTextFilterComponent() {
    return myFilterField;
  }

  @Override
  @NotNull
  public ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(createBranchComponent());
    actionGroup.add(createUserComponent());
    actionGroup.add(createDateComponent());
    actionGroup.add(createStructureFilterComponent());
    return actionGroup;
  }

  @NotNull
  @Override
  public VcsLogFilterCollection getFilters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return VcsLogFilterObject.collection(myBranchFilterModel.getBranchFilter(), myBranchFilterModel.getRevisionFilter(),
                                         myBranchFilterModel.getRangeFilter(),
                                         myTextFilterModel.getFilter1(), myTextFilterModel.getFilter2(),
                                         myStructureFilterModel.getFilter1(), myStructureFilterModel.getFilter2(),
                                         myDateFilterModel.getFilter(), myUserFilterModel.getFilter());
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
      myBranchFilterModel.setBranchFilter((VcsLogBranchFilter)filter);
    }
    else if (filter instanceof VcsLogStructureFilter) {
      myStructureFilterModel.setStructureFilter((VcsLogStructureFilter)filter);
    }
  }

  @NotNull
  protected FilterActionComponent createBranchComponent() {
    return new FilterActionComponent(() -> new BranchFilterPopupComponent(myUiProperties, myBranchFilterModel).initUi());
  }

  @NotNull
  protected FilterActionComponent createUserComponent() {
    return new FilterActionComponent(() -> new UserFilterPopupComponent(myUiProperties, myLogData, myUserFilterModel).initUi());
  }

  @NotNull
  protected FilterActionComponent createDateComponent() {
    return new FilterActionComponent(() -> new DateFilterPopupComponent(myDateFilterModel).initUi());
  }

  @NotNull
  protected FilterActionComponent createStructureFilterComponent() {
    return new FilterActionComponent(
      () -> new StructureFilterPopupComponent(myUiProperties, myStructureFilterModel, myColorManager).initUi());
  }

  protected static class FilterActionComponent extends DumbAwareAction implements CustomComponentAction {

    @NotNull private final Computable<? extends JComponent> myComponentCreator;

    public FilterActionComponent(@NotNull Computable<? extends JComponent> componentCreator) {
      myComponentCreator = componentCreator;
    }

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return myComponentCreator.compute();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }

  public static class BranchFilterModel extends FilterModel<BranchFilters> {
    @NotNull private final VcsLogStorage myStorage;
    @NotNull private final Collection<VirtualFile> myRoots;
    @Nullable private Collection<VirtualFile> myVisibleRoots;
    @NotNull private final Computable<? extends VcsLogDataPack> myDataPackProvider;

    BranchFilterModel(@NotNull Computable<? extends VcsLogDataPack> dataPackProvider,
                      @NotNull VcsLogStorage storage,
                      @NotNull Collection<VirtualFile> roots,
                      @NotNull MainVcsLogUiProperties properties,
                      @Nullable VcsLogFilterCollection filters) {
      super(properties);
      myStorage = storage;
      myRoots = roots;
      myDataPackProvider = dataPackProvider;

      if (filters != null) {
        saveFilterToProperties(new BranchFilters(filters.get(BRANCH_FILTER), filters.get(REVISION_FILTER), filters.get(RANGE_FILTER)));
      }
    }

    @Override
    public void setFilter(@Nullable BranchFilters filters) {
      triggerFilterSet(filters, BranchFilters::getBranchFilter, myFilter, BRANCH_FILTER.getName());
      triggerFilterSet(filters, BranchFilters::getRevisionFilter, myFilter, REVISION_FILTER.getName());
      triggerFilterSet(filters, BranchFilters::getRangeFilter, myFilter, RANGE_FILTER.getName());

      super.setFilter(filters);
    }

    @Override
    protected void saveFilterToProperties(@Nullable BranchFilters filters) {
      if (filters == null || filters.getBranchFilter() == null) {
        myUiProperties.saveFilterValues(BRANCH_FILTER.getName(), null);
      }
      else {
        myUiProperties.saveFilterValues(BRANCH_FILTER.getName(), getBranchFilterValues(filters.getBranchFilter()));
      }

      if (filters == null || filters.getRevisionFilter() == null) {
        myUiProperties.saveFilterValues(REVISION_FILTER.getName(), null);
      }
      else {
        myUiProperties.saveFilterValues(REVISION_FILTER.getName(), getRevisionFilterValues(filters.getRevisionFilter()));
      }

      if (filters == null || filters.getRangeFilter() == null) {
        myUiProperties.saveFilterValues(RANGE_FILTER.getName(), null);
      }
      else {
        myUiProperties.saveFilterValues(RANGE_FILTER.getName(), getRangeFilterValues(filters.getRangeFilter()));
      }
    }

    @Nullable
    @Override
    protected BranchFilters getFilterFromProperties() {
      List<String> branchFilterValues = myUiProperties.getFilterValues(BRANCH_FILTER.getName());
      VcsLogBranchFilter branchFilter = null;
      if (branchFilterValues != null) {
        branchFilter = createBranchFilter(branchFilterValues);
      }

      List<String> revisionFilterValues = myUiProperties.getFilterValues(REVISION_FILTER.getName());
      VcsLogRevisionFilter revisionFilter = null;
      if (revisionFilterValues != null) {
        revisionFilter = createRevisionFilter(revisionFilterValues);
      }

      List<String> rangeFilterValues = myUiProperties.getFilterValues(RANGE_FILTER.getName());
      VcsLogRangeFilter rangeFilter = null;
      if (rangeFilterValues != null) {
        rangeFilter = createRangeFilter(rangeFilterValues);
      }

      if (branchFilter == null && revisionFilter == null && rangeFilter == null) {
        return null;
      }
      return new BranchFilters(branchFilter, revisionFilter, rangeFilter);
    }

    public void onStructureFilterChanged(@Nullable VcsLogRootFilter rootFilter,
                                         @Nullable VcsLogStructureFilter structureFilter) {
      if (rootFilter == null && structureFilter == null) {
        myVisibleRoots = null;
      }
      else {
        myVisibleRoots = VcsLogUtil.getAllVisibleRoots(myRoots, rootFilter, structureFilter);
      }
    }

    @Nullable
    public Collection<VirtualFile> getVisibleRoots() {
      return myVisibleRoots;
    }

    @NotNull
    VcsLogDataPack getDataPack() {
      return myDataPackProvider.compute();
    }

    @Nullable
    protected VcsLogBranchFilter createBranchFilter(@NotNull List<String> values) {
      return VcsLogFilterObject.fromBranchPatterns(values,
                                                   ContainerUtil.map2Set(getDataPack().getRefs().getBranches(), VcsRef::getName));
    }

    @Nullable
    protected VcsLogRevisionFilter createRevisionFilter(@NotNull List<String> values) {
      Pattern pattern = Pattern.compile("\\[(.*)\\](" + VcsLogUtil.HASH_REGEX.pattern() + ")");
      return VcsLogFilterObject.fromCommits(ContainerUtil.mapNotNull(values, s -> {
        Matcher matcher = pattern.matcher(s);
        if (!matcher.matches()) {
          if (s.length() == VcsLogUtil.FULL_HASH_LENGTH && VcsLogUtil.HASH_REGEX.matcher(s).matches()) {
            CommitId commitId = findCommitId(HashImpl.build(s));
            if (commitId != null) return commitId;
          }
          LOG.warn("Could not parse '" + s + "' while creating revision filter");
          return null;
        }
        MatchResult result = matcher.toMatchResult();
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(result.group(1));
        if (root == null) {
          LOG.warn("Root '" + result.group(1) + "' does not exist");
          return null;
        }
        else if (!myRoots.contains(root)) {
          LOG.warn("Root '" + result.group(1) + "' is not registered");
          return null;
        }
        return new CommitId(HashImpl.build(result.group(2)), root);
      }));
    }

    @Nullable
    private static VcsLogRangeFilter createRangeFilter(@NotNull List<String> values) {
      if (values.isEmpty()) return null;
      return VcsLogFilterObject.fromRange(ContainerUtil.map(values, value -> {
        String TWO_DOTS = "..";
        int twoDots = value.indexOf(TWO_DOTS);
        if (twoDots <= 0) {
          LOG.error("Incorrect range filter value: " + values);
          return null;
        }
        return new VcsLogRangeFilter.RefRange(value.substring(0, twoDots), value.substring(twoDots + TWO_DOTS.length()));
      }));
    }

    @Nullable
    private CommitId findCommitId(@NotNull Hash hash) {
      for (VirtualFile root : myRoots) {
        CommitId commitId = new CommitId(hash, root);
        if (myStorage.containsCommit(commitId)) {
          return commitId;
        }
      }
      return null;
    }

    @NotNull
    private static List<String> getBranchFilterValues(@NotNull VcsLogBranchFilter filter) {
      return new ArrayList<>(ContainerUtil.sorted(filter.getTextPresentation()));
    }

    @NotNull
    private static List<String> getRevisionFilterValues(@NotNull VcsLogRevisionFilter filter) {
      return ContainerUtil.map(filter.getHeads(), id -> "[" + id.getRoot().getPath() + "]" + id.getHash().asString());
    }

    @NotNull
    private static List<String> getRangeFilterValues(@NotNull VcsLogRangeFilter rangeFilter) {
      return new ArrayList<>(rangeFilter.getTextPresentation());
    }

    @NotNull
    private static List<String> getRevisionFilter2Presentation(@NotNull VcsLogRevisionFilter filter) {
      return ContainerUtil.map(filter.getHeads(), id -> id.getHash().asString());
    }

    void setBranchFilter(@NotNull VcsLogBranchFilter branchFilter) {
      setFilter(new BranchFilters(branchFilter, null, null));
    }

    public void setRangeFilter(@NotNull VcsLogRangeFilter rangeFilter) {
      setFilter(new BranchFilters(null, null, rangeFilter));
    }

    @NotNull
    static List<String> getFilterPresentation(@NotNull BranchFilters filters) {
      List<String> branchFilterValues = filters.getBranchFilter() == null ? emptyList() : getBranchFilterValues(filters.getBranchFilter());
      List<String> revisionFilterValues = filters.getRevisionFilter() == null ? emptyList() :
                                          getRevisionFilter2Presentation(filters.getRevisionFilter());
      List<String> rangeFilterValues = filters.getRangeFilter() == null ? emptyList() : getRangeFilterValues(filters.getRangeFilter());
      return ContainerUtil.concat(branchFilterValues, revisionFilterValues, rangeFilterValues);
    }

    @Nullable
    BranchFilters createFilterFromPresentation(@NotNull List<String> values) {
      List<String> hashes = new ArrayList<>();
      List<String> branches = new ArrayList<>();
      List<String> ranges = new ArrayList<>();
      for (String s : values) {
        int twoDots = s.indexOf("..");
        if (twoDots > 0 && twoDots == s.lastIndexOf("..")) {
          ranges.add(s);
        }
        else if (s.length() == VcsLogUtil.FULL_HASH_LENGTH && VcsLogUtil.HASH_REGEX.matcher(s).matches()) {
          hashes.add(s);
        }
        else {
          branches.add(s);
        }
      }
      VcsLogBranchFilter branchFilter = branches.isEmpty() ? null : createBranchFilter(branches);
      VcsLogRevisionFilter hashFilter = hashes.isEmpty() ? null : createRevisionFilter(hashes);
      VcsLogRangeFilter refDiffFilter = ranges.isEmpty() ? null : createRangeFilter(ranges);
      return new BranchFilters(branchFilter, hashFilter, refDiffFilter);
    }

    @Nullable
    public VcsLogBranchFilter getBranchFilter() {
      BranchFilters filter = getFilter();
      if (filter == null) return null;
      return filter.getBranchFilter();
    }

    @Nullable
    public VcsLogRevisionFilter getRevisionFilter() {
      BranchFilters filter = getFilter();
      if (filter == null) return null;
      return filter.getRevisionFilter();
    }

    @Nullable
    public VcsLogRangeFilter getRangeFilter() {
      BranchFilters filter = getFilter();
      if (filter == null) return null;
      return filter.getRangeFilter();
    }
  }

  protected static class TextFilterModel extends FilterModel.PairFilterModel<VcsLogTextFilter, VcsLogHashFilter> {
    @Nullable private String myText;

    TextFilterModel(@NotNull MainVcsLogUiProperties properties,
                    @Nullable VcsLogFilterCollection filters,
                    @NotNull Disposable parentDisposable) {
      super(TEXT_FILTER, HASH_FILTER, properties, filters);
      if (filters != null) {
        VcsLogTextFilter textFilter = filters.get(TEXT_FILTER);
        if (textFilter != null) {
          myUiProperties.set(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE, textFilter.matchesCase());
          myUiProperties.set(MainVcsLogUiProperties.TEXT_FILTER_REGEX, textFilter.isRegex());
        }
      }
      VcsLogUiProperties.PropertiesChangeListener listener = new VcsLogUiProperties.PropertiesChangeListener() {
        @Override
        public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
          if (MainVcsLogUiProperties.TEXT_FILTER_REGEX.equals(property) ||
              MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE.equals(property)) {
            if (getFilter1() != null) {
              myFilter = getFilterFromProperties();
              notifyFiltersChanged();
            }
          }
        }
      };
      properties.addChangeListener(listener);
      Disposer.register(parentDisposable, () -> properties.removeChangeListener(listener));
    }

    @Nullable
    @Override
    protected FilterPair<VcsLogTextFilter, VcsLogHashFilter> getFilterFromProperties() {
      FilterPair<VcsLogTextFilter, VcsLogHashFilter> filterPair = super.getFilterFromProperties();
      if (filterPair == null) return null;

      VcsLogTextFilter textFilter = filterPair.getFilter1();
      VcsLogHashFilter hashFilter = filterPair.getFilter2();

      // check filters correctness
      if (textFilter != null && StringUtil.isEmptyOrSpaces(textFilter.getText())) {
        LOG.warn("Saved text filter is empty. Removing.");
        textFilter = null;
      }

      if (textFilter != null) {
        VcsLogHashFilter hashFilterFromText = VcsLogFilterObject.fromHash(textFilter.getText());
        if (!Objects.equals(hashFilter, hashFilterFromText)) {
          LOG.warn("Saved hash filter " + hashFilter + " is inconsistent with text filter." +
                   " Replacing with " + hashFilterFromText);
          hashFilter = hashFilterFromText;
        }
      }
      else if (hashFilter != null && !hashFilter.getHashes().isEmpty()) {
        textFilter = createTextFilter(StringUtil.join(hashFilter.getHashes(), " "));
        LOG.warn("Saved hash filter " + hashFilter +
                 " is inconsistent with empty text filter. Using text filter " + textFilter);
      }

      return new FilterPair<>(textFilter, hashFilter);
    }

    @NotNull
    String getText() {
      if (myText != null) {
        return myText;
      }
      else if (getFilter1() != null) {
        return getFilter1().getText();
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
      return getFilter1() == null || !myText.equals(getFilter1().getText());
    }

    @Override
    public void setFilter(@Nullable FilterPair<VcsLogTextFilter, VcsLogHashFilter> filter) {
      super.setFilter(filter);
      myText = null;
    }

    @NotNull
    @Override
    protected List<String> getFilter1Values(@NotNull VcsLogTextFilter filter) {
      return Collections.singletonList(filter.getText());
    }

    @NotNull
    @Override
    protected List<String> getFilter2Values(@NotNull VcsLogHashFilter filter) {
      return new ArrayList<>(filter.getHashes());
    }

    @Nullable
    @Override
    protected VcsLogTextFilter createFilter1(@NotNull List<String> values) {
      return createTextFilter(ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(values)));
    }

    @Nullable
    @Override
    protected VcsLogHashFilter createFilter2(@NotNull List<String> values) {
      return VcsLogFilterObject.fromHashes(values);
    }

    @NotNull
    private VcsLogTextFilter createTextFilter(@NotNull String text) {
      return VcsLogFilterObject.fromPattern(text,
                                            myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_REGEX),
                                            myUiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE));
    }

    public void setFilterText(@NotNull String text) {
      if (StringUtil.isEmptyOrSpaces(text)) {
        setFilter(null);
      }
      else {
        VcsLogTextFilter textFilter = createTextFilter(text);
        VcsLogHashFilter hashFilter = VcsLogFilterObject.fromHash(text);
        setFilter(new FilterPair<>(textFilter, hashFilter));
      }
    }
  }

  public static class FileFilterModel extends FilterModel.PairFilterModel<VcsLogStructureFilter, VcsLogRootFilter> {
    @NotNull private static final String DIR = "dir:";
    @NotNull private static final String FILE = "file:";
    @NotNull private final Set<VirtualFile> myRoots;

    public FileFilterModel(@NotNull Set<VirtualFile> roots,
                           @NotNull MainVcsLogUiProperties uiProperties,
                           @Nullable VcsLogFilterCollection filters) {
      super(STRUCTURE_FILTER, ROOT_FILTER, uiProperties, filters);
      myRoots = roots;
    }

    @Override
    @NotNull
    protected List<String> getFilter1Values(@NotNull VcsLogStructureFilter filter) {
      return getFilterValues(filter);
    }

    @Override
    @NotNull
    protected List<String> getFilter2Values(@NotNull VcsLogRootFilter filter) {
      return getRootFilterValues(filter);
    }

    @NotNull
    public static List<String> getRootFilterValues(@NotNull VcsLogRootFilter filter) {
      return ContainerUtil.map(filter.getRoots(), VirtualFile::getPath);
    }

    @NotNull
    static List<String> getFilterValues(@NotNull VcsLogStructureFilter filter) {
      return ContainerUtil.map(filter.getFiles(), path -> (path.isDirectory() ? DIR : FILE) + path.getPath());
    }

    @Override
    @NotNull
    protected VcsLogStructureFilter createFilter1(@NotNull List<String> values) {
      return createStructureFilter(values);
    }

    @Override
    @Nullable
    protected VcsLogRootFilter createFilter2(@NotNull List<String> values) {
      List<VirtualFile> selectedRoots = new ArrayList<>();
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
          LOG.warn("Can not filter by root that does not exist " + path);
        }
      }
      if (selectedRoots.isEmpty()) return null;
      return VcsLogFilterObject.fromRoots(selectedRoots);
    }

    @Nullable
    protected VcsLogRootFilter getRootFilter() {
      return getFilter2();
    }

    @Nullable
    public VcsLogStructureFilter getStructureFilter() {
      return getFilter1();
    }

    @NotNull
    Set<VirtualFile> getRoots() {
      return myRoots;
    }

    protected void setStructureFilter(@NotNull VcsLogStructureFilter filter) {
      setFilter(new FilterPair<>(filter, null));
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
  }

  private static class DateFilterModel extends FilterModel.SingleFilterModel<VcsLogDateFilter> {
    DateFilterModel(@NotNull MainVcsLogUiProperties uiProperties, @Nullable VcsLogFilterCollection filters) {
      super(DATE_FILTER, uiProperties, filters);
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

  private class UserFilterModel extends FilterModel.SingleFilterModel<VcsLogUserFilter> {
    UserFilterModel(@NotNull MainVcsLogUiProperties uiProperties, @Nullable VcsLogFilterCollection filters) {
      super(USER_FILTER, uiProperties, filters);
    }

    @NotNull
    @Override
    protected VcsLogUserFilter createFilter(@NotNull List<String> values) {
      return VcsLogFilterObject.fromUserNames(values, myLogData);
    }

    @NotNull
    @Override
    protected List<String> getFilterValues(@NotNull VcsLogUserFilter filter) {
      return new ArrayList<>(filter.getValuesAsText());
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
      myTextFilterModel.setFilterText(getText());
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

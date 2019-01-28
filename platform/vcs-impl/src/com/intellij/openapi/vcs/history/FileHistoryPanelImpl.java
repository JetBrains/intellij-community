// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.VcsInternalDataKeys;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vcs.vfs.VcsVirtualFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.dualView.CellWrapper;
import com.intellij.ui.dualView.DualView;
import com.intellij.ui.dualView.DualViewColumnInfo;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.TreeItem;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.List;
import java.util.*;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;

/**
 * author: lesya
 */
public class FileHistoryPanelImpl extends JPanel implements DataProvider, Disposable, EditorColorsListener, CopyProvider {
  private static final String COMMIT_MESSAGE_TITLE = VcsBundle.message("label.selected.revision.commit.message");
  private static final String VCS_HISTORY_POPUP_ACTION_GROUP = "VcsHistoryInternalGroup.Popup";
  private static final String VCS_HISTORY_TOOLBAR_ACTION_GROUP = "VcsHistoryInternalGroup.Toolbar";

  public static final DataKey<VcsFileRevision> PREVIOUS_REVISION_FOR_DIFF = DataKey.create("PREVIOUS_VCS_FILE_REVISION_FOR_DIFF");

  private final String myHelpId;

  @NotNull private final AbstractVcs myVcs;
  private final VcsHistoryProvider myProvider;
  @NotNull private final FileHistoryRefresherI myRefresherI;
  @NotNull private final FilePath myFilePath;
  @Nullable private final VcsRevisionNumber myStartingRevision;
  @NotNull private final Map<VcsRevisionNumber, Integer> myRevisionsOrder = ContainerUtil.newHashMap();
  @NotNull private final Map<VcsFileRevision, VirtualFile> myRevisionToVirtualFile = ContainerUtil.newHashMap();
  @NotNull private final DetailsPanel myDetails;
  @NotNull private final DualView myDualView;
  @Nullable private final JComponent myAdditionalDetails;
  @Nullable private final Consumer<VcsFileRevision> myRevisionSelectionListener;

  @NotNull private VcsHistorySession myHistorySession;
  private VcsFileRevision myBottomRevisionForShowDiff;
  private List<Object> myTargetSelection;
  private boolean myIsStaticAndEmbedded;
  private Splitter myDetailsSplitter;
  private Splitter mySplitter;

  public FileHistoryPanelImpl(@NotNull AbstractVcs vcs,
                              @NotNull FilePath filePath,
                              @NotNull VcsHistorySession session,
                              VcsHistoryProvider provider,
                              @NotNull FileHistoryRefresherI refresherI,
                              final boolean isStaticEmbedded) {
    this(vcs, filePath, null, session, provider, refresherI, isStaticEmbedded);
  }

  public FileHistoryPanelImpl(@NotNull AbstractVcs vcs,
                              @NotNull FilePath filePath,
                              @Nullable VcsRevisionNumber startingRevision,
                              @NotNull VcsHistorySession session,
                              VcsHistoryProvider provider,
                              @NotNull FileHistoryRefresherI refresherI,
                              final boolean isStaticEmbedded) {
    super(new BorderLayout());
    myHelpId = provider.getHelpId() != null ? provider.getHelpId() : "reference.versionControl.toolwindow.history";

    myIsStaticAndEmbedded = false;
    myVcs = vcs;
    myProvider = provider;
    myRefresherI = refresherI;
    myHistorySession = session;
    myFilePath = filePath;
    myStartingRevision = startingRevision;
    myDetails = new DetailsPanel(vcs.getProject());

    refreshRevisionsOrder();

    final VcsDependentHistoryComponents components = provider.getUICustomization(session, this);
    myAdditionalDetails = components.getDetailsComponent();
    myRevisionSelectionListener = components.getRevisionListener();

    final DualViewColumnInfo[] columns = createColumnList(vcs.getProject(), provider, components.getColumns());
    @NonNls String storageKey = "FileHistory." + provider.getClass().getName();
    final HistoryAsTreeProvider treeHistoryProvider = myHistorySession.getHistoryAsTreeProvider();
    if (treeHistoryProvider != null) {
      myDualView = new DualView(new TreeNodeOnVcsRevision(null, treeHistoryProvider.createTreeOn(myHistorySession.getRevisionList())),
                                columns, storageKey, myVcs.getProject());
    }
    else {
      myDualView =
        new DualView(new TreeNodeOnVcsRevision(null, ContainerUtil.map(myHistorySession.getRevisionList(), TreeItem::new)), columns,
                     storageKey, myVcs.getProject());
      myDualView.switchToTheFlatMode();
    }
    new TableSpeedSearch(myDualView.getFlatView()).setComparator(new SpeedSearchComparator(false));
    final TableLinkMouseListener listener = new TableLinkMouseListener();
    listener.installOn(myDualView.getFlatView());
    listener.installOn(myDualView.getTreeView());
    myDualView.setEmptyText(CommonBundle.getLoadingTreeNodeText());

    setupDualView(fillActionGroup(true, new DefaultActionGroup(null, false)));
    if (isStaticEmbedded) {
      setIsStaticAndEmbedded(true);
    }
    DefaultActionGroup toolbarGroup = new DefaultActionGroup(null, false);
    fillActionGroup(false, toolbarGroup);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.FILEHISTORY_VIEW_TOOLBAR, toolbarGroup,
                                                                            isStaticEmbedded);
    JComponent centerPanel = createCenterPanel();
    toolbar.setTargetComponent(centerPanel);
    for (AnAction action : toolbarGroup.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), centerPanel);
    }

    add(centerPanel, BorderLayout.CENTER);
    add(toolbar.getComponent(), isStaticEmbedded ? BorderLayout.NORTH : BorderLayout.WEST);

    chooseView();

    Disposer.register(vcs.getProject(), this);
  }

  private static void makeBold(Component component) {
    if (component instanceof JComponent) {
      JComponent jComponent = (JComponent)component;
      Font font = jComponent.getFont();
      if (font != null) {
        jComponent.setFont(font.deriveFont(Font.BOLD));
      }
    }
    else if (component instanceof Container) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        makeBold(container.getComponent(i));
      }
    }
  }

  @NotNull
  public static String getPresentableText(@NotNull VcsFileRevision revision, boolean withMessage) {
    // implementation reflected by com.intellij.vcs.log.ui.frame.VcsLogGraphTable.getPresentableText()
    StringBuilder sb = new StringBuilder();
    sb.append(VcsUtil.getShortRevisionString(revision.getRevisionNumber())).append(" ");
    sb.append(revision.getAuthor());
    long time = revision.getRevisionDate().getTime();
    sb.append(" on ").append(DateFormatUtil.formatDate(time)).append(" at ").append(DateFormatUtil.formatTime(time));
    if (revision instanceof VcsFileRevisionEx) {
      if (!Comparing.equal(revision.getAuthor(), ((VcsFileRevisionEx)revision).getCommitterName())) {
        sb.append(" (committed by ").append(((VcsFileRevisionEx)revision).getCommitterName()).append(")");
      }
    }
    if (withMessage) {
      sb.append(" ").append(MessageColumnInfo.getSubject(revision));
    }
    return sb.toString();
  }

  /**
   * Checks if the given historyPanel shows the history for given path and revision number.
   */
  static boolean sameHistories(@NotNull FileHistoryPanelImpl historyPanel,
                               @NotNull FilePath filePath2,
                               @Nullable VcsRevisionNumber startingRevision2) {
    return sameHistories(historyPanel.myFilePath, historyPanel.myStartingRevision, filePath2, startingRevision2);
  }

  public static boolean sameHistories(@NotNull FilePath filePath1, @Nullable VcsRevisionNumber startingRevision1,
                                      @NotNull FilePath filePath2, @Nullable VcsRevisionNumber startingRevision2) {
    String existingRevision = startingRevision1 == null ? null : startingRevision1.asString();
    String newRevision = startingRevision2 == null ? null : startingRevision2.asString();
    return filePath1.equals(filePath2) && Comparing.equal(existingRevision, newRevision);
  }

  @NotNull
  private DualViewColumnInfo[] createColumnList(@NotNull Project project,
                                                @NotNull VcsHistoryProvider provider,
                                                @Nullable ColumnInfo[] additionalColumns) {
    ArrayList<DualViewColumnInfo> columns = new ArrayList<>();
    columns.add(new TreeNodeColumnInfoWrapper<>(
      new RevisionColumnInfo(comparing(revision -> myRevisionsOrder.get(revision.getRevisionNumber()), reverseOrder()))));
    if (!provider.isDateOmittable()) columns.add(new TreeNodeColumnInfoWrapper<>(new DateColumnInfo()));
    columns.add(new TreeNodeColumnInfoWrapper<>(new AuthorColumnInfo()));
    if (additionalColumns != null) {
      for (ColumnInfo additionalColumn : additionalColumns) {
        columns.add(new TreeNodeColumnInfoWrapper(additionalColumn));
      }
    }
    columns.add(new TreeNodeColumnInfoWrapper<>(new MessageColumnInfo(project)));
    return columns.toArray(new DualViewColumnInfo[0]);
  }

  @CalledInAwt
  public void setHistorySession(@NotNull VcsHistorySession session) {
    if (myTargetSelection == null) {
      myTargetSelection = myDualView.getFlatView().getSelectedObjects();
    }

    myHistorySession = session;
    refreshRevisionsOrder();
    HistoryAsTreeProvider treeHistoryProvider = session.getHistoryAsTreeProvider();

    if (myHistorySession.getRevisionList().isEmpty()) {
      adjustEmptyText();
    }

    if (treeHistoryProvider != null) {
      myDualView.setRoot(new TreeNodeOnVcsRevision(null,
                                                   treeHistoryProvider.createTreeOn(myHistorySession.getRevisionList())),
                         myTargetSelection);
    }
    else {
      myDualView.setRoot(new TreeNodeOnVcsRevision(null, ContainerUtil.map(myHistorySession.getRevisionList(), TreeItem::new)),
                         myTargetSelection);
    }

    mySplitter.revalidate();
    mySplitter.repaint();
    myDualView.expandAll();
    myDualView.repaint();
  }

  @CalledInAwt
  public void finishRefresh() {
    if (myHistorySession.getHistoryAsTreeProvider() != null) {
      // scroll tree view to most recent change
      final TreeTableView treeView = myDualView.getTreeView();
      final int lastRow = treeView.getRowCount() - 1;
      if (lastRow >= 0) {
        treeView.scrollRectToVisible(treeView.getCellRect(lastRow, 0, true));
      }
    }
    myTargetSelection = null;

    mySplitter.revalidate();
    mySplitter.repaint();
  }

  private void adjustEmptyText() {
    VirtualFile virtualFile = myFilePath.getVirtualFile();
    if ((virtualFile == null || !virtualFile.isValid()) && !myFilePath.getIOFile().exists()) {
      myDualView.setEmptyText("File " + myFilePath.getName() + " not found");
    }
    else if (VcsCachingHistory.getHistoryLock(myVcs, VcsBackgroundableActions.CREATE_HISTORY_SESSION, myFilePath, myStartingRevision)
      .isLocked()) {
      myDualView.setEmptyText(CommonBundle.getLoadingTreeNodeText());
    }
    else {
      myDualView.setEmptyText(StatusText.DEFAULT_EMPTY_TEXT);
    }
  }

  private void setupDualView(@NotNull DefaultActionGroup group) {
    myDualView.setShowGrid(true);
    PopupHandler.installPopupHandler(myDualView.getTreeView(), group, ActionPlaces.UPDATE_POPUP, ActionManager.getInstance());
    PopupHandler.installPopupHandler(myDualView.getFlatView(), group, ActionPlaces.UPDATE_POPUP, ActionManager.getInstance());
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myDualView, true));

    myDualView.addListSelectionListener(e -> updateMessage());
    myDualView.setRootVisible(false);
    myDualView.expandAll();

    myDualView.setTreeCellRenderer(new MyTreeCellRenderer(myDualView.getTree().getCellRenderer(), () -> myHistorySession));
    myDualView.setCellWrapper(new MyCellWrapper(() -> myHistorySession));

    myDualView.installDoubleClickHandler(EmptyAction.wrap(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON)));

    myDualView.getFlatView().getTableViewModel().setSortable(true);
    RowSorter<? extends TableModel> rowSorter = myDualView.getFlatView().getRowSorter();
    if (rowSorter != null) {
      rowSorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(0, SortOrder.DESCENDING)));
    }
  }

  private void updateMessage() {
    //noinspection unchecked
    List<TreeNodeOnVcsRevision> selection = (List<TreeNodeOnVcsRevision>)myDualView.getSelection();
    myDetails.update(selection);
    if (selection.isEmpty()) {
      return;
    }
    if (myRevisionSelectionListener != null) {
      myRevisionSelectionListener.consume(selection.get(0).getRevision());
    }
  }

  @NotNull
  protected JComponent createCenterPanel() {
    mySplitter = new OnePixelSplitter(true, "vcs.history.splitter.proportion", 0.6f);
    mySplitter.setFirstComponent(myDualView);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myDetails);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));

    myDetailsSplitter = new OnePixelSplitter(false, "vcs.history.details.splitter.proportion", 0.5f);
    myDetailsSplitter.setFirstComponent(scrollPane);
    myDetailsSplitter.setSecondComponent(myAdditionalDetails);

    setupDetails();
    return mySplitter;
  }

  private void setupDetails() {
    boolean showDetails = !myIsStaticAndEmbedded && VcsConfiguration.getInstance(myVcs.getProject()).SHOW_FILE_HISTORY_DETAILS;
    myDualView.setViewBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
    mySplitter.setSecondComponent(showDetails ? myDetailsSplitter : null);
  }

  private void chooseView() {
    if (VcsConfiguration.getInstance(myVcs.getProject()).SHOW_FILE_HISTORY_AS_TREE) {
      myDualView.switchToTheTreeMode();
    }
    else {
      myDualView.switchToTheFlatMode();
    }
  }

  @NotNull
  private DefaultActionGroup fillActionGroup(boolean popup, DefaultActionGroup result) {
    if (popup) {
      result.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    }

    AnAction actionGroup = ActionManager.getInstance().getAction(popup ? VCS_HISTORY_POPUP_ACTION_GROUP : VCS_HISTORY_TOOLBAR_ACTION_GROUP);
    result.add(actionGroup);
    AnAction[] additionalActions =
      myProvider.getAdditionalActions(() -> ApplicationManager.getApplication().invokeAndWait(() -> myRefresherI.refresh(true)));
    if (additionalActions != null) {
      for (AnAction additionalAction : additionalActions) {
        if (popup || additionalAction.getTemplatePresentation().getIcon() != null) {
          result.add(additionalAction);
        }
      }
    }
    if (!myIsStaticAndEmbedded) {
      result.add(new MyShowDetailsAction());
    }

    if (!popup && myHistorySession.getHistoryAsTreeProvider() != null) {
      result.add(new MyShowAsTreeAction());
    }

    return result;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      VcsFileRevision[] selectedRevisions = getSelectedRevisions();
      if (selectedRevisions.length != 1) return null;
      VcsFileRevision firstSelectedRevision = ArrayUtil.getFirstElement(selectedRevisions);
      if (!myHistorySession.isContentAvailable(firstSelectedRevision)) {
        return null;
      }
      VirtualFile virtualFileForRevision = createVirtualFileForRevision(firstSelectedRevision);
      if (virtualFileForRevision != null) {
        return new OpenFileDescriptor(myVcs.getProject(), virtualFileForRevision);
      }
      else {
        return null;
      }
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myVcs.getProject();
    }
    else if (VcsDataKeys.VCS_FILE_REVISION.is(dataId)) {
      return ArrayUtil.getFirstElement(getSelectedRevisions());
    }
    else if (VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION.is(dataId)) {
      return !myHistorySession.hasLocalSource();
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      return myVcs.getKeyInstanceMethod();
    }
    else if (VcsDataKeys.VCS_FILE_REVISIONS.is(dataId)) {
      return getSelectedRevisions();
    }
    else if (VcsDataKeys.REMOTE_HISTORY_CHANGED_LISTENER.is(dataId)) {
      return (Consumer<String>)s -> myDualView.rebuild();
    }
    else if (VcsDataKeys.CHANGES.is(dataId)) {
      return getChanges();
    }
    else if (VcsDataKeys.VCS_VIRTUAL_FILE.is(dataId)) {
      VcsFileRevision[] selectedRevisions = getSelectedRevisions();
      if (selectedRevisions.length == 0) return null;
      return createVirtualFileForRevision(ArrayUtil.getFirstElement(selectedRevisions));
    }
    else if (VcsDataKeys.FILE_PATH.is(dataId)) {
      return myFilePath;
    }
    else if (VcsDataKeys.IO_FILE.is(dataId)) {
      return myFilePath.getIOFile();
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      VirtualFile virtualFile = myFilePath.getVirtualFile();
      return virtualFile == null || !virtualFile.isValid() ? null : virtualFile;
    }
    else if (VcsDataKeys.FILE_HISTORY_PANEL.is(dataId)) {
      return this;
    }
    else if (VcsDataKeys.HISTORY_SESSION.is(dataId)) {
      return myHistorySession;
    }
    else if (VcsDataKeys.HISTORY_PROVIDER.is(dataId)) {
      return myProvider;
    }
    else if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }
    else if (PREVIOUS_REVISION_FOR_DIFF.is(dataId)) {
      TableView<TreeNodeOnVcsRevision> flatView = myDualView.getFlatView();
      if (flatView.getSelectedRow() == (flatView.getRowCount() - 1)) {
        // no previous
        return myBottomRevisionForShowDiff != null ? myBottomRevisionForShowDiff : VcsFileRevision.NULL;
      }
      else {
        return flatView.getRow(flatView.getSelectedRow() + 1).getRevision();
      }
    }
    else if (VcsInternalDataKeys.FILE_HISTORY_REFRESHER.is(dataId)) {
      return myRefresherI;
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    return null;
  }

  @Nullable
  private Change[] getChanges() {
    final VcsFileRevision[] revisions = getSelectedRevisions();

    if (revisions.length > 0) {
      Arrays.sort(revisions, comparing(VcsRevisionDescription::getRevisionNumber));

      for (VcsFileRevision revision : revisions) {
        if (!myHistorySession.isContentAvailable(revision)) {
          return null;
        }
      }

      final ContentRevision startRevision = new LoadedContentRevision(myFilePath, revisions[0], myVcs.getProject());
      final ContentRevision endRevision = (revisions.length == 1) ? new CurrentContentRevision(myFilePath) :
                                          new LoadedContentRevision(myFilePath, revisions[revisions.length - 1], myVcs.getProject());

      return new Change[]{new Change(startRevision, endRevision)};
    }
    return null;
  }

  private VirtualFile createVirtualFileForRevision(VcsFileRevision revision) {
    if (!myRevisionToVirtualFile.containsKey(revision)) {
      FilePath filePath = (revision instanceof VcsFileRevisionEx ? ((VcsFileRevisionEx)revision).getPath() : myFilePath);
      myRevisionToVirtualFile.put(revision, filePath.isDirectory()
                                            ? new VcsVirtualFolder(filePath.getPath(), null, VcsFileSystem.getInstance())
                                            : new VcsVirtualFile(filePath.getPath(), revision, VcsFileSystem.getInstance()));
    }
    return myRevisionToVirtualFile.get(revision);
  }

  @NotNull
  public VcsFileRevision[] getSelectedRevisions() {
    //noinspection unchecked
    List<TreeNodeOnVcsRevision> selection = (List<TreeNodeOnVcsRevision>)myDualView.getSelection();
    VcsFileRevision[] result = new VcsFileRevision[selection.size()];
    for (int i = 0; i < selection.size(); i++) {
      result[i] = selection.get(i).getRevision();
    }
    return result;
  }

  @Override
  public void dispose() {
    myDualView.dispose();
  }

  private void refreshRevisionsOrder() {
    final List<VcsFileRevision> list = myHistorySession.getRevisionList();
    myRevisionsOrder.clear();

    int cnt = 0;
    for (VcsFileRevision revision : list) {
      myRevisionsOrder.put(revision.getRevisionNumber(), cnt);
      ++cnt;
    }
  }

  public void setIsStaticAndEmbedded(boolean isStaticAndEmbedded) {
    myIsStaticAndEmbedded = isStaticAndEmbedded;
    myDualView.setZipByHeight(isStaticAndEmbedded);
    myDualView.getFlatView().updateColumnSizes();
    if (myIsStaticAndEmbedded) {
      myDualView.getFlatView().getTableHeader().setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
      myDualView.getTreeView().getTableHeader().setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
      myDualView.getFlatView().setBorder(null);
      myDualView.getTreeView().setBorder(null);
    }
  }

  public void setBottomRevisionForShowDiff(VcsFileRevision bottomRevisionForShowDiff) {
    myBottomRevisionForShowDiff = bottomRevisionForShowDiff;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof FileHistoryPanelImpl && sameHistories((FileHistoryPanelImpl)obj, myFilePath, myStartingRevision);
  }

  @Override
  public int hashCode() {
    int result = myFilePath.hashCode();
    result = 31 * result + (myStartingRevision != null ? myStartingRevision.asString().hashCode() : 0); // NB: asString to conform to equals
    return result;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    String text = StringUtil.join(getSelectedRevisions(), revision -> getPresentableText(revision, true), "\n");
    CopyPasteManager.getInstance().setContents(new StringSelection(text));
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    //noinspection unchecked
    return ((List<TreeNodeOnVcsRevision>)myDualView.getSelection()).size() > 0;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void globalSchemeChange(EditorColorsScheme scheme) {
    updateMessage();
  }

  private class TreeNodeColumnInfoWrapper<T extends Comparable<T>> extends FileHistoryColumnWrapper<T> {
    TreeNodeColumnInfoWrapper(@NotNull ColumnInfo<VcsFileRevision, T> additionalColumn) {
      super(additionalColumn);
    }

    @Override
    protected DualView getDualView() {
      return myDualView;
    }

    @Override
    public TableCellRenderer getCustomizedRenderer(TreeNodeOnVcsRevision revision, @Nullable TableCellRenderer renderer) {
      if (renderer instanceof BaseHistoryCellRenderer) {
        ((BaseHistoryCellRenderer)renderer)
          .setCurrentRevision(myHistorySession.isCurrentRevision(revision.getRevision().getRevisionNumber()));
      }
      return renderer;
    }
  }

  private abstract static class BaseHistoryCellRenderer extends ColoredTableCellRenderer {
    private boolean myIsCurrentRevision = false;

    protected SimpleTextAttributes getDefaultAttributes() {
      return myIsCurrentRevision ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    public void setCurrentRevision(boolean currentRevision) {
      myIsCurrentRevision = currentRevision;
    }
  }

  public static class RevisionColumnInfo extends ColumnInfo<VcsFileRevision, VcsRevisionNumber> {
    @Nullable private final Comparator<VcsFileRevision> myComparator;
    @NotNull private final ColoredTableCellRenderer myRenderer;

    public RevisionColumnInfo(@Nullable Comparator<VcsFileRevision> comparator) {
      super(VcsBundle.message("column.name.revision.version"));
      myComparator = comparator;
      myRenderer = new BaseHistoryCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          setOpaque(selected);
          append(VcsUtil.getShortRevisionString((VcsRevisionNumber)value), getDefaultAttributes());
          SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected);
        }
      };
    }

    @Nullable
    @Override
    public VcsRevisionNumber valueOf(VcsFileRevision revision) {
      return revision.getRevisionNumber();
    }

    @Nullable
    @Override
    public Comparator<VcsFileRevision> getComparator() {
      return myComparator;
    }

    @Override
    public String getPreferredStringValue() {
      return StringUtil.repeatSymbol('m', 10);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(VcsFileRevision revision) {
      return myRenderer;
    }
  }

  public static class DateColumnInfo extends ColumnInfo<VcsFileRevision, Date> {
    @NotNull private final ColoredTableCellRenderer myRenderer;

    public DateColumnInfo() {
      super(VcsBundle.message("column.name.revision.date"));

      myRenderer = new BaseHistoryCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          setOpaque(selected);
          Date date = (Date)value;
          if (date != null) {
            append(DateFormatUtil.formatPrettyDateTime(date), getDefaultAttributes());
          }
          SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected);
        }
      };
    }

    @NotNull
    @Override
    public Comparator<VcsFileRevision> getComparator() {
      return comparing(revision -> valueOf(revision));
    }

    @Nullable
    @Override
    public Date valueOf(VcsFileRevision revision) {
      return revision.getRevisionDate();
    }

    @Override
    public String getPreferredStringValue() {
      return DateFormatUtil.formatPrettyDateTime(Clock.getTime() + 1000);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(VcsFileRevision revision) {
      return myRenderer;
    }
  }

  private static class AuthorCellRenderer extends BaseHistoryCellRenderer {
    private String myTooltipText;

    /**
     * @noinspection MethodNamesDifferingOnlyByCase
     */
    public void setTooltipText(final String text) {
      myTooltipText = text;
    }

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      setToolTipText(myTooltipText);
      if (selected || hasFocus) {
        setBackground(table.getSelectionBackground());
        setForeground(table.getSelectionForeground());
      }
      else {
        setBackground(table.getBackground());
        setForeground(table.getForeground());
      }
      if (value != null) append(value.toString(), getDefaultAttributes());
      SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected);
    }
  }

  public static class AuthorColumnInfo extends ColumnInfo<VcsFileRevision, String> {
    private final TableCellRenderer AUTHOR_RENDERER = new AuthorCellRenderer();

    public AuthorColumnInfo() {
      super(VcsBundle.message("column.name.revision.list.author"));
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(VcsFileRevision revision) {
      return AUTHOR_RENDERER;
    }

    @Override
    public TableCellRenderer getCustomizedRenderer(VcsFileRevision revision, TableCellRenderer renderer) {
      if (renderer instanceof AuthorCellRenderer) {
        if (revision instanceof VcsFileRevisionEx) {
          VcsFileRevisionEx ex = (VcsFileRevisionEx)revision;
          StringBuilder sb = new StringBuilder(StringUtil.notNullize(ex.getAuthor()));
          if (ex.getAuthorEmail() != null) sb.append(" &lt;").append(ex.getAuthorEmail()).append("&gt;");
          if (ex.getCommitterName() != null && !Comparing.equal(ex.getAuthor(), ex.getCommitterName())) {
            sb.append(", via ").append(ex.getCommitterName());
            if (ex.getCommitterEmail() != null) sb.append(" &lt;").append(ex.getCommitterEmail()).append("&gt;");
          }
          ((AuthorCellRenderer)renderer).setTooltipText(sb.toString());
        }
      }

      return renderer;
    }

    @Nullable
    @Override
    public String valueOf(VcsFileRevision revision) {
      if (revision instanceof VcsFileRevisionEx) {
        if (!Comparing.equal(revision.getAuthor(), ((VcsFileRevisionEx)revision).getCommitterName())) {
          return revision.getAuthor() + "*";
        }
      }
      return revision.getAuthor();
    }

    @Override
    @NonNls
    public String getPreferredStringValue() {
      return StringUtil.repeatSymbol('m', 14);
    }

    @NotNull
    @Override
    public Comparator<VcsFileRevision> getComparator() {
      return comparing(revision -> valueOf(revision));
    }
  }

  public static class MessageColumnInfo extends ColumnInfo<VcsFileRevision, String> {
    private final ColoredTableCellRenderer myRenderer;
    private final IssueLinkRenderer myIssueLinkRenderer;

    public MessageColumnInfo(Project project) {
      super(COMMIT_MESSAGE_TITLE);
      myRenderer = new BaseHistoryCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          setOpaque(selected);
          if (value instanceof String) {
            String message = (String)value;
            myIssueLinkRenderer.appendTextWithLinks(message, getDefaultAttributes());
            SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected);
          }
        }
      };
      myIssueLinkRenderer = new IssueLinkRenderer(project, myRenderer);
    }

    @NotNull
    public static String getSubject(@NotNull VcsFileRevision object) {
      final String originalMessage = object.getCommitMessage();
      if (originalMessage == null) return "";

      int index = StringUtil.indexOfAny(originalMessage, "\n\r");
      return index == -1 ? originalMessage : originalMessage.substring(0, index);
    }

    @Nullable
    @Override
    public String valueOf(VcsFileRevision revision) {
      return getSubject(revision);
    }

    @Override
    public String getPreferredStringValue() {
      return StringUtil.repeatSymbol('m', 80);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(VcsFileRevision revision) {
      return myRenderer;
    }

    @NotNull
    @Override
    public Comparator<VcsFileRevision> getComparator() {
      return comparing(revision -> valueOf(revision));
    }
  }

  private static class LoadedContentRevision implements ByteBackedContentRevision {
    private final FilePath myFile;
    private final VcsFileRevision myRevision;
    private final Project myProject;

    private LoadedContentRevision(final FilePath file, final VcsFileRevision revision, final Project project) {
      myFile = file;
      myRevision = revision;
      myProject = project;
    }

    @Override
    public String getContent() throws VcsException {
      try {
        return VcsHistoryUtil.loadRevisionContentGuessEncoding(myRevision, myFile.getVirtualFile(), myProject);
      }
      catch (IOException e) {
        throw new VcsException(VcsBundle.message("message.text.cannot.load.revision", e.getLocalizedMessage()));
      }
    }

    @Nullable
    @Override
    public byte[] getContentAsBytes() throws VcsException {
      try {
        return VcsHistoryUtil.loadRevisionContent(myRevision);
      }
      catch (IOException e) {
        throw new VcsException(VcsBundle.message("message.text.cannot.load.revision", e.getLocalizedMessage()));
      }
    }

    @Override
    @NotNull
    public FilePath getFile() {
      return myFile;
    }

    @Override
    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return myRevision.getRevisionNumber();
    }
  }

  private static class MyTreeCellRenderer implements TreeCellRenderer {
    private final TreeCellRenderer myDefaultCellRenderer;
    private final Getter<? extends VcsHistorySession> myHistorySession;

    MyTreeCellRenderer(final TreeCellRenderer defaultCellRenderer, final Getter<? extends VcsHistorySession> historySession) {
      myDefaultCellRenderer = defaultCellRenderer;
      myHistorySession = historySession;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      final Component result = myDefaultCellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

      final TreePath path = tree.getPathForRow(row);
      if (path == null) return result;
      TreeNodeOnVcsRevision node = row >= 0 ? ((TreeNodeOnVcsRevision)path.getLastPathComponent()) : null;

      if (node != null) {
        if (myHistorySession.get().isCurrentRevision(node.getRevision().getRevisionNumber())) {
          makeBold(result);
        }
        if (!selected && myHistorySession.get().isCurrentRevision(node.getRevision().getRevisionNumber())) {
          result.setBackground(new JBColor(new Color(188, 227, 231), new Color(188, 227, 231)));
        }
        ((JComponent)result).setOpaque(false);
      }
      else if (selected) {
        result.setBackground(UIUtil.getTableSelectionBackground());
      }
      else {
        result.setBackground(UIUtil.getTableBackground());
      }

      return result;
    }
  }

  private static class MyCellWrapper implements CellWrapper {
    private final Getter<? extends VcsHistorySession> myHistorySession;

    MyCellWrapper(final Getter<? extends VcsHistorySession> historySession) {
      myHistorySession = historySession;
    }

    @Override
    public void wrap(Component component,
                     JTable table,
                     Object value,
                     boolean isSelected,
                     boolean hasFocus,
                     int row,
                     int column,
                     Object treeNode) {
      VcsFileRevision revision = ((TreeNodeOnVcsRevision)treeNode).getRevision();
      if (myHistorySession.get().isCurrentRevision(revision.getRevisionNumber())) {
        makeBold(component);
      }
    }
  }

  private class MyShowAsTreeAction extends ToggleAction implements DumbAware {
    MyShowAsTreeAction() {
      super(VcsBundle.message("action.name.show.files.as.tree"), null, PlatformIcons.SMALL_VCS_CONFIGURABLE);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return VcsConfiguration.getInstance(myVcs.getProject()).SHOW_FILE_HISTORY_AS_TREE;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      VcsConfiguration.getInstance(myVcs.getProject()).SHOW_FILE_HISTORY_AS_TREE = state;
      chooseView();
    }
  }

  private class MyShowDetailsAction extends ToggleAction implements DumbAware {

    MyShowDetailsAction() {
      super("Show Details", "Display details panel", AllIcons.Actions.PreviewDetailsVertically);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return VcsConfiguration.getInstance(myVcs.getProject()).SHOW_FILE_HISTORY_DETAILS;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      VcsConfiguration.getInstance(myVcs.getProject()).SHOW_FILE_HISTORY_DETAILS = state;
      setupDetails();
    }
  }
}

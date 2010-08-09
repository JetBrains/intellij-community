/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.PanelWithActionsAndCloseButton;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.openapi.vcs.changes.committed.AbstractCalledLater;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vcs.ui.ReplaceFileConfirmationDialog;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.dualView.CellWrapper;
import com.intellij.ui.dualView.DualTreeElement;
import com.intellij.ui.dualView.DualView;
import com.intellij.ui.dualView.DualViewColumnInfo;
import com.intellij.util.*;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.TableViewModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * author: lesya
 */
public class FileHistoryPanelImpl<S extends CommittedChangeList, U extends ChangeBrowserSettings> extends PanelWithActionsAndCloseButton {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.ui.FileHistoryDialog");

  private final JEditorPane myComments;
  private JComponent myAdditionalDetails;
  private Consumer<VcsFileRevision> myListener;
  private String myOriginalComment = "";
  private final DefaultActionGroup myPopupActions;

  private final AbstractVcs myVcs;
  private final VcsHistoryProvider myProvider;
  private final AnnotationProvider myAnnotationProvider;
  private VcsHistorySession myHistorySession;
  private final FilePath myFilePath;
  private final Runnable myRefresher;
  private final DualView myDualView;
  private final Map<VcsRevisionNumber, Integer> myRevisionsOrder;

  private final Alarm myUpdateAlarm;

  private volatile boolean myInRefresh;
  private List<Object> myTargetSelection;
  private final AsynchConsumer<VcsHistorySession> myHistoryPanelRefresh;

  private static final String COMMIT_MESSAGE_TITLE = VcsBundle.message("label.selected.revision.commit.message");
  @NonNls private static final String VCS_HISTORY_ACTIONS_GROUP = "VcsHistoryActionsGroup";

  private final Comparator<VcsFileRevision> myRevisionsInOrderComparator = new Comparator<VcsFileRevision>() {
    @Override
    public int compare(VcsFileRevision o1, VcsFileRevision o2) {
      // descending
      return Comparing.compare(myRevisionsOrder.get(o2.getRevisionNumber()), myRevisionsOrder.get(o1.getRevisionNumber()));
    }
  };
  
  private final DualViewColumnInfo REVISION =
    new VcsColumnInfo<VcsRevisionNumber>(VcsBundle.message("column.name.revision.version")) {
      protected VcsRevisionNumber getDataOf(VcsFileRevision object) {
        return object.getRevisionNumber();
      }

      public String valueOf(VcsFileRevision object) {
        return object.getRevisionNumber().asString();
      }

      @Override
      public void sort(@NotNull List<VcsFileRevision> vcsFileRevisions) {
        Collections.sort(vcsFileRevisions, myRevisionsInOrderComparator);
      }

      @Override
      public String getPreferredStringValue() {
        return "123.4567";
      }
    };

  private static final DualViewColumnInfo DATE = new VcsColumnInfo<String>(VcsBundle.message("column.name.revision.date")) {
    protected String getDataOf(VcsFileRevision object) {
      Date date = object.getRevisionDate();
      if (date == null) return "";
      return DATE_FORMAT.format(date);
    }

    public int compare(VcsFileRevision o1, VcsFileRevision o2) {
      return o1.getRevisionDate().compareTo(o2.getRevisionDate());
    }

    @Override
    public String getPreferredStringValue() {
      return DATE_FORMAT.format(new Date());
    }
  };

  private static final DualViewColumnInfo AUTHOR = new VcsColumnInfo<String>(VcsBundle.message("column.name.revision.list.author")) {
    protected String getDataOf(VcsFileRevision object) {
      return object.getAuthor();
    }

    @Override
    @NonNls
    public String getPreferredStringValue() {
      return "author_author";
    }
  };
  private JLabel myLoadingLabel;
  private Splitter mySplitter;


  private static class MessageRenderer extends ColoredTableCellRenderer {
    private final IssueLinkRenderer myIssueLinkRenderer;

    public MessageRenderer(Project project) {
      myIssueLinkRenderer = new IssueLinkRenderer(project, this);
    }

    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setOpaque(selected);
      String message = (String) value;
      myIssueLinkRenderer.appendTextWithLinks(message);
      setToolTipText(message);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      // skip border erasing code in SimpleColoredRenderer
      doPaint(g);
    }
  }

  private static class MessageColumnInfo extends VcsColumnInfo<String> {
    private final MessageRenderer myRenderer;

    public MessageColumnInfo(Project project) {
      super(FileHistoryPanelImpl.COMMIT_MESSAGE_TITLE);
      myRenderer = new MessageRenderer(project);
    }

    protected String getDataOf(VcsFileRevision object) {
      final String originalMessage = object.getCommitMessage();
      if (originalMessage != null) {
        String commitMessage = originalMessage.trim();
        int index13 = commitMessage.indexOf('\r');
        int index10 = commitMessage.indexOf('\n');

        if (index10 < 0 && index13 < 0) {
          return commitMessage;
        }
        else {
          return commitMessage.substring(0, getSuitableIndex(index10, index13)) + "...";
        }
      }
      else {
        return "";
      }
    }

    private static int getSuitableIndex(int index10, int index13) {
      if (index10 < 0) {
        return index13;
      }
      else if (index13 < 0) {
        return index10;
      }
      else {
        return Math.min(index10, index13);
      }
    }

    @Override
    public String getPreferredStringValue() {
      return StringUtil.repeatSymbol('a', 125);
    }

    public TableCellRenderer getRenderer(VcsFileRevision p0) {
      return myRenderer;
    }
  }

  private final DualViewColumnInfo[] COLUMNS;

  private static final DateFormat DATE_FORMAT = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT);
  private final Map<VcsFileRevision, VirtualFile> myRevisionToVirtualFile = new HashMap<VcsFileRevision, VirtualFile>();

  public FileHistoryPanelImpl(AbstractVcs vcs,
                              FilePath filePath, VcsHistorySession session,
                              VcsHistoryProvider provider,
                              AnnotationProvider annotationProvider,
                              ContentManager contentManager, final Runnable refresher) {
    super(contentManager, provider.getHelpId() != null ? provider.getHelpId() : "reference.versionControl.toolwindow.history");
    myVcs = vcs;
    myProvider = provider;
    myAnnotationProvider = annotationProvider;
    myRefresher = refresher;
    myHistorySession = session;         
    myFilePath = filePath;

    COLUMNS = createColumnList(myVcs.getProject(), provider, session);

    myComments = new JEditorPane(UIUtil.HTML_MIME, "");
    myComments.setPreferredSize(new Dimension(150, 100));
    myComments.setEditable(false);
    myComments.setBackground(UIUtil.getComboBoxDisabledBackground());
    myComments.addHyperlinkListener(new BrowserHyperlinkListener());

    myRevisionsOrder = new HashMap<VcsRevisionNumber, Integer>();
    refreshRevisionsOrder();

    replaceTransferable();

    myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    final HistoryAsTreeProvider treeHistoryProvider = myHistorySession.getHistoryAsTreeProvider();

    @NonNls String storageKey = "FileHistory." + provider.getClass().getName();
    if (treeHistoryProvider != null) {
      myDualView = new DualView(new TreeNodeOnVcsRevision(null, treeHistoryProvider.createTreeOn(myHistorySession.getRevisionList())),
                                COLUMNS, storageKey, myVcs.getProject());
    }
    else {
      myDualView = new DualView(new TreeNodeOnVcsRevision(null, wrapWithTreeElements(myHistorySession.getRevisionList())), COLUMNS,
                                storageKey, myVcs.getProject());
      myDualView.switchToTheFlatMode();
    }
    final TableLinkMouseListener listener = new TableLinkMouseListener();
    listener.install(myDualView.getFlatView());
    listener.install(myDualView.getTreeView());

    createDualView();

    myPopupActions = createPopupActions();

    myHistoryPanelRefresh = new AsynchConsumer<VcsHistorySession>() {
      public void finished() {
        myInRefresh = false;
        myTargetSelection = null;

        myLoadingLabel.setVisible(false);
        mySplitter.revalidate();
        mySplitter.repaint();
      }
      public void consume(VcsHistorySession vcsHistorySession) {
        FileHistoryPanelImpl.this.refresh(vcsHistorySession);
      }
    };

    // todo react to event?
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        if (myVcs.getProject().isDisposed()) {
          return;
        }
        final boolean refresh = (! myInRefresh) && myHistorySession.shouldBeRefreshed();
        myUpdateAlarm.cancelAllRequests();
        myUpdateAlarm.addRequest(this, 20000);

        if (refresh) {
          refreshImpl();
        }
      }
    }, 20000);

    init();

    chooseView();
  }

  private void replaceTransferable() {
    final TransferHandler originalTransferHandler = myComments.getTransferHandler();

    final TransferHandler newHandler = new TransferHandler("copy") {
      @Override
      public void exportAsDrag(final JComponent comp, final InputEvent e, final int action) {
        originalTransferHandler.exportAsDrag(comp, e, action);
      }

      @Override
      public void exportToClipboard(final JComponent comp, final Clipboard clip, final int action) throws IllegalStateException {
        if ((action == COPY || action == MOVE)
                && (getSourceActions(comp) & action) != 0) {

            Transferable t = new TextTransferrable(myComments.getText(), myOriginalComment);
            if (t != null) {
                try {
                    clip.setContents(t, null);
                    exportDone(comp, t, action);
                    return;
                } catch (IllegalStateException ise) {
                    exportDone(comp, t, NONE);
                    throw ise;
                }
            }
        }

        exportDone(comp, null, NONE);
      }

      @Override
      public boolean importData(final JComponent comp, final Transferable t) {
        return originalTransferHandler.importData(comp, t);
      }

      @Override
      public boolean canImport(final JComponent comp, final DataFlavor[] transferFlavors) {
        return originalTransferHandler.canImport(comp, transferFlavors);
      }

      @Override
      public int getSourceActions(final JComponent c) {
        return originalTransferHandler.getSourceActions(c);
      }

      @Override
      public Icon getVisualRepresentation(final Transferable t) {
        return originalTransferHandler.getVisualRepresentation(t);
      }
    };

    myComments.setTransferHandler(newHandler);
  }

  private DualViewColumnInfo[] createColumnList(Project project, VcsHistoryProvider provider, final VcsHistorySession session) {
    final VcsDependentHistoryComponents components = provider.getUICustomization(session, this);
    myAdditionalDetails = components.getDetailsComponent();
    myListener = components.getRevisionListener();

    ArrayList<DualViewColumnInfo> columns = new ArrayList<DualViewColumnInfo>();
    if (provider.isDateOmittable()) {
      columns.addAll(Arrays.asList(REVISION, AUTHOR));
    }
    else {
      columns.addAll(Arrays.asList(REVISION, DATE, AUTHOR));
    }

    columns.addAll(wrapAdditionalColumns(components.getColumns()));
    columns.add(new MessageColumnInfo(project));
    return columns.toArray(new DualViewColumnInfo[columns.size()]);
  }

  private static Collection<DualViewColumnInfo> wrapAdditionalColumns(ColumnInfo[] additionalColumns) {
    ArrayList<DualViewColumnInfo> result = new ArrayList<DualViewColumnInfo>();
    if (additionalColumns != null) {
      for (ColumnInfo additionalColumn : additionalColumns) {
        result.add(new MyColumnWrapper(additionalColumn));
      }
    }
    return result;
  }

  private static List<TreeItem<VcsFileRevision>> wrapWithTreeElements(List<VcsFileRevision> revisions) {
    ArrayList<TreeItem<VcsFileRevision>> result = new ArrayList<TreeItem<VcsFileRevision>>();
    for (final VcsFileRevision revision : revisions) {
      result.add(new TreeItem<VcsFileRevision>(revision));
    }
    return result;
  }

  private void refresh(final VcsHistorySession session) {
    myHistorySession = session;
    refreshRevisionsOrder();
    HistoryAsTreeProvider treeHistoryProvider = session.getHistoryAsTreeProvider();

    if (treeHistoryProvider != null) {
      myDualView.setRoot(new TreeNodeOnVcsRevision(null,
        treeHistoryProvider.createTreeOn(myHistorySession.getRevisionList())), myTargetSelection);
    }
    else {
      myDualView.setRoot(new TreeNodeOnVcsRevision(null,
        wrapWithTreeElements(myHistorySession.getRevisionList())), myTargetSelection);
    }

    myDualView.expandAll();
    myDualView.repaint();
  }

  protected void addActionsTo(DefaultActionGroup group) {
    addToGroup(false, group);
  }

  private void createDualView() {
    myDualView.setShowGrid(true);
    myDualView.getTreeView().addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance()
          .createActionPopupMenu(ActionPlaces.UPDATE_POPUP, myPopupActions);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    myDualView.getFlatView().addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance()
          .createActionPopupMenu(ActionPlaces.UPDATE_POPUP, myPopupActions);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    myDualView.requestFocus();
    myDualView.setSelectionInterval(0, 0);


    myDualView.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateMessage();
      }
    });

    myDualView.setRootVisible(false);

    myDualView.expandAll();

    final TreeCellRenderer defaultCellRenderer = myDualView.getTree().getCellRenderer();

    final Getter<VcsHistorySession> sessionGetter = new Getter<VcsHistorySession>() {
      public VcsHistorySession get() {
        return myHistorySession;
      }
    };
    myDualView.setTreeCellRenderer(new MyTreeCellRenderer(defaultCellRenderer, sessionGetter));

    myDualView.setCellWrapper(new MyCellWrapper(sessionGetter));

    TableViewModel sortableModel = myDualView.getFlatView().getTableViewModel();
    sortableModel.setSortable(true);

    if (null == null) {
      sortableModel.sortByColumn(0, SortableColumnModel.SORT_DESCENDING);
    }
    else {
      sortableModel.sortByColumn(getColumnIndex(null), SortableColumnModel.SORT_DESCENDING);
    }

  }

  private int getColumnIndex(final ColumnInfo defaultColumnToSortBy) {
    for (int i = 0; i < COLUMNS.length; i++) {
      DualViewColumnInfo dualViewColumnInfo = COLUMNS[i];
      if (dualViewColumnInfo instanceof MyColumnWrapper) {
        if (((MyColumnWrapper)dualViewColumnInfo).getOriginalColumn() == defaultColumnToSortBy) {
          return i;
        }
      }
    }
    return 0;
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

  private void updateMessage() {
    List selection = getSelection();
    final VcsFileRevision revision;
    if (selection.size() != 1) {
      revision = null;
      myComments.setText("");
      myOriginalComment = "";
    }
    else {
      revision = getFirstSelectedRevision();
      final String message = revision.getCommitMessage();
      myOriginalComment = message;
      @NonNls final String text = IssueLinkHtmlRenderer.formatTextIntoHtml(myVcs.getProject(), message);
      myComments.setText(text);
      myComments.setCaretPosition(0);
    }
    if (myListener != null) {
      myListener.consume(revision);
    }
  }


  private void showDifferences(Project project, VcsFileRevision revision1, VcsFileRevision revision2) {

    try {
      revision1.loadContent();
      revision2.loadContent();

      VcsFileRevision left = revision1;
      VcsFileRevision right = revision2;
      if (VcsHistoryUtil.compare(revision1, revision2) > 0) {
        left = revision2;
        right = revision1;
      }
      final byte[] content1 = left.getContent();
      if (content1 == null) throw new VcsException("Failed to load content for revision " + left.getRevisionNumber().asString());
      final byte[] content2 = right.getContent();
      if (content2 == null) throw new VcsException("Failed to load content for revision " + right.getRevisionNumber().asString());


      SimpleDiffRequest diffData = new SimpleDiffRequest(myVcs.getProject(), myFilePath.getPresentableUrl());

      diffData.addHint(DiffTool.HINT_SHOW_FRAME);

      Document doc = myFilePath.getDocument();

      Charset charset = myFilePath.getCharset();
      FileType fileType = myFilePath.getFileType();
      diffData.setContentTitles(left.getRevisionNumber().asString(), right.getRevisionNumber().asString());
      diffData.setContents(createContent(project, content1, left, doc, charset, fileType),
                           createContent(project, content2, right, doc, charset, fileType));
      DiffManager.getInstance().getDiffTool().show(diffData);
    }
    catch (final VcsException e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(VcsBundle.message("message.text.cannot.show.differences", e.getLocalizedMessage()),
                                   VcsBundle.message("message.title.show.differences"));
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (ProcessCanceledException ex) {
      return;
    }
  }

  private static DiffContent createContent(Project project,
                                           byte[] content1,
                                           VcsFileRevision revision,
                                           Document doc,
                                           Charset charset,
                                           FileType fileType) {
    if (isCurrent(revision) && (doc != null)) return new DocumentContent(project, doc);
    return new BinaryContent(content1, charset, fileType);
  }

  private static boolean isCurrent(VcsFileRevision revision) {
    return revision instanceof CurrentRevision;
  }


  protected JComponent createCenterPanel() {
    mySplitter = new Splitter(true, getSplitterProportion());
    mySplitter.setDividerWidth(4);
    //splitter.getDivider().setBackground(UIUtil.getBgFillColor(splitter.getDivider()).brighter());

    mySplitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          setSplitterProportionTo((Float)evt.getNewValue());
        }
      }
    });

    final Splitter detailsSplitter = new Splitter(false, 0.5f);
    JPanel commentGroup = new JPanel(new BorderLayout(4, 4));
    final JLabel commentLabel = new JLabel(COMMIT_MESSAGE_TITLE + ":") {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getWidth(), 21);
      }
    };
    commentGroup.add(commentLabel, BorderLayout.NORTH);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myComments);
    pane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.LEFT | (myAdditionalDetails == null ? 0 : SideBorder.BOTTOM)));

    commentGroup.add(pane, BorderLayout.CENTER);
    detailsSplitter.setFirstComponent(commentGroup);
    detailsSplitter.setSecondComponent(myAdditionalDetails);

    final JPanel wrapper = new JPanel(new BorderLayout());
    myLoadingLabel = new JLabel("Loading...");
    //myLoadingLabel.setVisible(false);
    myLoadingLabel.setBackground(UIUtil.getToolTipBackground());
    wrapper.add(myLoadingLabel, BorderLayout.NORTH);

    myDualView.setViewBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.BOTTOM));
    wrapper.add(myDualView, BorderLayout.CENTER);

    mySplitter.setFirstComponent(wrapper);
    mySplitter.setSecondComponent(detailsSplitter);
    return mySplitter;
  }

  private void chooseView() {
    if (showTree()) {
      myDualView.switchToTheTreeMode();
    }
    else {
      myDualView.switchToTheFlatMode();
    }
  }

  private boolean showTree() {
    return getConfiguration().SHOW_FILE_HISTORY_AS_TREE;
  }

  private void setSplitterProportionTo(Float newProportion) {
    getConfiguration().FILE_HISTORY_SPLITTER_PROPORTION = newProportion.floatValue();
  }

  private float getSplitterProportion() {
    return getConfiguration().FILE_HISTORY_SPLITTER_PROPORTION;
  }

  private VcsConfiguration getConfiguration() {
    return VcsConfiguration.getInstance(myVcs.getProject());
  }

  private DefaultActionGroup createPopupActions() {
    return addToGroup(true, new DefaultActionGroup(null, false));

  }

  private DefaultActionGroup addToGroup(boolean popup, DefaultActionGroup result) {
    if (popup) {
      result.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    }

    final MyDiffAction diffAction = new MyDiffAction();
    result.add(diffAction);
    if (!popup) {
      diffAction.registerCustomShortcutSet(new CustomShortcutSet(
        new Shortcut[] {
          CommonShortcuts.getDiff().getShortcuts() [0],
          CommonShortcuts.DOUBLE_CLICK_1.getShortcuts() [0]
        }), myDualView.getFlatView());
      diffAction.registerCustomShortcutSet(new CustomShortcutSet(
        new Shortcut[] {
          CommonShortcuts.getDiff().getShortcuts() [0],
          CommonShortcuts.DOUBLE_CLICK_1.getShortcuts() [0]
        }), myDualView.getTreeView());
    }
    else {
      diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), this);
    }

    final AnAction diffGroup = ActionManager.getInstance().getAction(VCS_HISTORY_ACTIONS_GROUP);
    if (diffGroup != null) result.add(diffGroup);    

    result.add(new CreatePatchFromChangesAction() {
      public void update(final AnActionEvent e) {
        e.getPresentation().setVisible(true);
        // in order to do not load changes only for action update
        final int selectionSize = getSelection().size();
        e.getPresentation().setEnabled((selectionSize > 0) && (selectionSize < 3));
      }
    });
    result.add(new MyGetVersionAction());
    result.add(new MyAnnotateAction());
    AnAction[] additionalActions = myProvider.getAdditionalActions(new Runnable() {
      public void run() {
        refreshImpl();
      }
    });
    if (additionalActions != null) {
      for (AnAction additionalAction : additionalActions) {
        result.add(additionalAction);
      }
    }
    result.add(new RefreshFileHistoryAction());

    if (!popup && supportsTree()) {
      result.add(new MyShowAsTreeAction());
    }

    return result;
  }

  private void refreshImpl() {
    new AbstractCalledLater(myVcs.getProject(), ModalityState.NON_MODAL) {
      public void run() {
        if (myInRefresh) return;
        myInRefresh = true;
        myTargetSelection = myDualView.getFlatView().getSelectedObjects();

        myLoadingLabel.setVisible(true);
        mySplitter.revalidate();
        mySplitter.repaint();

        myRefresher.run();
      }
    }.callMe();
  }

  public AsynchConsumer<VcsHistorySession> getHistoryPanelRefresh() {
    return myHistoryPanelRefresh;
  }

  private boolean supportsTree() {
    return myHistorySession != null && myHistorySession.getHistoryAsTreeProvider() != null;
  }

  private class MyShowAsTreeAction extends ToggleAction implements DumbAware {
    public MyShowAsTreeAction() {
      super(VcsBundle.message("action.name.show.files.as.tree"), null, Icons.SMALL_VCS_CONFIGURABLE);
    }

    public boolean isSelected(AnActionEvent e) {
      return getConfiguration().SHOW_FILE_HISTORY_AS_TREE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      getConfiguration().SHOW_FILE_HISTORY_AS_TREE = state;
      chooseView();
    }
  }

  private class MyDiffAction extends AbstractActionForSomeSelection {
    public MyDiffAction() {
      super(VcsBundle.message("action.name.compare"), VcsBundle.message("action.description.compare"), "diff", 2,
            FileHistoryPanelImpl.this);
    }

    protected void actionPerformed() {
      List<TreeNodeOnVcsRevision> sel = getSelection();

      int selectionSize = sel.size();
      if (selectionSize > 1) {
        showDifferences(myVcs.getProject(), sel.get(0), sel.get(sel.size() - 1));
      }
      else if (selectionSize == 1) {
        final VcsRevisionNumber currentRevisionNumber = myHistorySession.getCurrentRevisionNumber();
        if (currentRevisionNumber != null) {
          showDifferences(myVcs.getProject(), getFirstSelectedRevision(), new CurrentRevision(myFilePath.getVirtualFile(), currentRevisionNumber));
        }
      }
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final int selectionSize = getSelection().size();
      if (selectionSize == 1) {
        e.getPresentation().setText(VcsBundle.message("action.name.compare.with.local"));
        e.getPresentation().setDescription(VcsBundle.message("action.description.compare.with.local"));
      }
      else {
        e.getPresentation().setText(VcsBundle.message("action.name.compare"));
        e.getPresentation().setDescription(VcsBundle.message("action.description.compare"));
      }
    }

    public boolean isEnabled() {
      final int selectionSize = getSelection().size();
      if (selectionSize == 1) {
        return isDiffWithCurrentEnabled();
      }
      else if (selectionSize > 1) {
        return isDiffEnabled();
      }
      return false;
    }

    private boolean isDiffEnabled() {
      List<TreeNodeOnVcsRevision> sel = getSelection();
      return myHistorySession.isContentAvailable(sel.get(0)) && myHistorySession.isContentAvailable(sel.get(sel.size() - 1));
    }

    private boolean isDiffWithCurrentEnabled() {
      if (myHistorySession.getCurrentRevisionNumber() == null) return false;
      if (myFilePath.getVirtualFile() == null) return false;
      if (!myHistorySession.isContentAvailable(getFirstSelectedRevision())) return false;
      return true;
    }
  }

  private class MyGetVersionAction extends AbstractActionForSomeSelection {
    public MyGetVersionAction() {
      super(VcsBundle.message("action.name.get.file.content.from.repository"),
            VcsBundle.message("action.description.get.file.content.from.repository"), "get", 1, FileHistoryPanelImpl.this);
    }

    public void update(AnActionEvent e) {
      if (getVirtualParent() == null) {
        Presentation presentation = e.getPresentation();
        presentation.setVisible(false);
        presentation.setEnabled(false);
      }
      else {
        super.update(e);
      }
    }

    @Override
    public boolean isEnabled() {
      if (!super.isEnabled()) return false;
      if (!myHistorySession.isContentAvailable(getFirstSelectedRevision())) return false;
      return true;
    }

    protected void actionPerformed() {
      final VcsFileRevision revision = getFirstSelectedRevision();
      if (getVirtualFile() != null) {
        if (!new ReplaceFileConfirmationDialog(myVcs.getProject(), VcsBundle.message("acton.name.get.revision"))
          .confirmFor(new VirtualFile[]{getVirtualFile()})) {
          return;
        }
      }

      try {
        revision.loadContent();
      }
      catch (VcsException e) {
        Messages.showErrorDialog(VcsBundle.message("message.text.cannot.load.version", e.getLocalizedMessage()),
                                 VcsBundle.message("message.title.get.version"));
      }
      catch (ProcessCanceledException ex) {
        return;
      }
      getVersion(revision);
      refreshFile(revision);
    }

    private void refreshFile(VcsFileRevision revision) {
      Runnable refresh = null;
      final VirtualFile vf = getVirtualFile();
      if (vf == null) {
        final LocalHistoryAction action = startLocalHistoryAction(revision);
        final VirtualFile vp = getVirtualParent();
        if (vp != null) {
          refresh = new Runnable() {
            public void run() {
              vp.refresh(false, true, new Runnable() {
                public void run() {
                  myFilePath.refresh();
                  action.finish();
                }
              });
            }
          };
        }
      } else {
        refresh = new Runnable() {
          public void run() {
            vf.refresh(false, false);
          }
        };
      }
      if (refresh != null) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(refresh, "Refreshing files...", false, myVcs.getProject());
      }
    }

    private void getVersion(final VcsFileRevision revision) {
      final VirtualFile file = getVirtualFile();
      if ((file != null) && !file.isWritable()) {
        if (ReadonlyStatusHandler.getInstance(myVcs.getProject()).ensureFilesWritable(file).hasReadonlyFiles()) {
          return;
        }
      }

      LocalHistoryAction action = file != null ? startLocalHistoryAction(revision) : LocalHistoryAction.NULL;

      final byte[] revisionContent;
      try {
        revision.loadContent();
        revisionContent = revision.getContent();
      }
      catch (IOException e) {
        LOG.error(e);
        return;
      }
      catch (VcsException e) {
        Messages.showMessageDialog(VcsBundle.message("message.text.cannot.load.revision", e.getLocalizedMessage()),
                                   VcsBundle.message("message.title.get.revision.content"), Messages.getInformationIcon());
        return;
      }
      catch (ProcessCanceledException ex) {
        return;
      }
      final byte[] finalRevisionContent = revisionContent;
      try {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            CommandProcessor.getInstance().executeCommand(myVcs.getProject(), new Runnable() {
                public void run() {
                  try {
                    write(finalRevisionContent);
                  }
                  catch (IOException e) {
                    Messages.showMessageDialog(VcsBundle.message("message.text.cannot.save.content", e.getLocalizedMessage()),
                                               VcsBundle.message("message.title.get.revision.content"), Messages.getErrorIcon());
                  }
                }
              }, createGetActionTitle(revision), null);
          }
        });
        if (file != null) {
          VcsDirtyScopeManager.getInstance(myVcs.getProject()).fileDirty(file);
        }
      }
      finally {
        action.finish();
      }
    }

    private LocalHistoryAction startLocalHistoryAction(final VcsFileRevision revision) {
      return LocalHistory.getInstance().startAction(createGetActionTitle(revision));
    }

    private String createGetActionTitle(final VcsFileRevision revision) {
      return VcsBundle.message("action.name.for.file.get.version", getIOFile().getAbsolutePath(), revision.getRevisionNumber());
    }

    private File getIOFile() {
      return myFilePath.getIOFile();
    }

    private void write(byte[] revision) throws IOException {
      if (getVirtualFile() == null) {
        writeContentToIOFile(revision);
      }
      else {
        Document document = myFilePath.getDocument();
        if (document == null) {
          writeContentToFile(revision);
        }
        else {
          writeContentToDocument(document, revision);
        }
      }
    }

    private void writeContentToIOFile(byte[] revisionContent) throws IOException {
      FileOutputStream outputStream = new FileOutputStream(getIOFile());
      try {
        outputStream.write(revisionContent);
      }
      finally {
        outputStream.close();
      }
    }

    private void writeContentToFile(final byte[] revision) throws IOException {
      getVirtualFile().setBinaryContent(revision);
    }

    private void writeContentToDocument(final Document document, byte[] revisionContent) throws IOException {
      final String content = StringUtil.convertLineSeparators(new String(revisionContent, myFilePath.getCharset().name()));

      CommandProcessor.getInstance().executeCommand(myVcs.getProject(), new Runnable() {
        public void run() {
          document.replaceString(0, document.getTextLength(), content);
        }
      }, VcsBundle.message("message.title.get.version"), null);
    }

  }

  private class MyAnnotateAction extends AnAction {
    public MyAnnotateAction() {
      super(VcsBundle.message("annotate.action.name"), VcsBundle.message("annotate.action.description"),
            IconLoader.getIcon("/actions/annotate.png"));
    }

    private String key(final VirtualFile vf, final VcsFileRevision revision) {
      return vf.getPath();
    }

    public void update(AnActionEvent e) {
      VirtualFile revVFile = e.getData( VcsDataKeys.VCS_VIRTUAL_FILE );
      VcsFileRevision revision = e.getData( VcsDataKeys.VCS_FILE_REVISION );
      FileType fileType = revVFile == null ? null : revVFile.getFileType();
      boolean enabled = revision != null && revVFile != null && !fileType.isBinary();

      if (enabled) {
        final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(myVcs.getProject());
        enabled &= (! (((ProjectLevelVcsManagerImpl) plVcsManager).getBackgroundableActionHandler(
          VcsBackgroundableActions.ANNOTATE).isInProgress(key(revVFile, revision))));
      }

      e.getPresentation()
        .setEnabled(enabled &&
                    myHistorySession.isContentAvailable(revision) &&
                    myAnnotationProvider != null && myAnnotationProvider.isAnnotationValid(revision));
    }


    public void actionPerformed(AnActionEvent e) {
      final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
      final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
      if ((revision == null) || (revisionVirtualFile == null)) return;

      final BackgroundableActionEnabledHandler handler = ((ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(myVcs.getProject())).
        getBackgroundableActionHandler(VcsBackgroundableActions.ANNOTATE);
      handler.register(key(revisionVirtualFile, revision));

      final Ref<FileAnnotation> fileAnnotationRef = new Ref<FileAnnotation>();
      final Ref<VcsException> exceptionRef = new Ref<VcsException>();

      ProgressManager.getInstance().run(new Task.Backgroundable(myVcs.getProject(), VcsBundle.message("retrieving.annotations"), true,
          BackgroundFromStartOption.getInstance()) {
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            fileAnnotationRef.set(myAnnotationProvider.annotate(revisionVirtualFile, revision));
          }
          catch (VcsException e) {
            exceptionRef.set(e);
          }
        }

        @Override
        public void onCancel() {
          onSuccess();
        }

        @Override
        public void onSuccess() {
          handler.completed(key(revisionVirtualFile, revision));

          if (! exceptionRef.isNull()) {
            AbstractVcsHelper.getInstance(myProject).showError(exceptionRef.get(), VcsBundle.message("operation.name.annotate"));
          }
          if (fileAnnotationRef.isNull()) return;

          AbstractVcsHelper.getInstance(myProject).showAnnotation(fileAnnotationRef.get(), revisionVirtualFile, myVcs);
        }
      });
    }
  }

  public Object getData(String dataId) {
    VcsFileRevision firstSelectedRevision = getFirstSelectedRevision();
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      List selectedItems = getSelection();
      if (selectedItems.size() != 1) return null;
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
    else if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myVcs.getProject();
    }
    else if (VcsDataKeys.VCS_FILE_REVISION.is(dataId)) {
      return firstSelectedRevision;
    }
    else if (VcsDataKeys.VCS_FILE_REVISIONS.is(dataId)) {
      return getSelectedRevisions();
    }
    else if (VcsDataKeys.CHANGES.is(dataId)) {
      return getChanges();
    }
    else if (VcsDataKeys.VCS_VIRTUAL_FILE.is(dataId)) {
      if (firstSelectedRevision == null) return null;
      return createVirtualFileForRevision(firstSelectedRevision);
    }
    else if (VcsDataKeys.FILE_PATH.is(dataId)) {
      return myFilePath;
    }
    else if (VcsDataKeys.IO_FILE.is(dataId)) {
      return myFilePath.getIOFile();
    }
    else if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) {
      if (getVirtualFile() == null) return null;
      if (getVirtualFile().isValid()) {
        return getVirtualFile();
      }
      else {
        return null;
      }
    }
    else if (VcsDataKeys.FILE_HISTORY_PANEL.is(dataId)) {
      return this;
    }
    else {
      return super.getData(dataId);
    }
  }

  @Nullable
  private Change[] getChanges() {
    final VcsFileRevision[] revisions = getSelectedRevisions();

    if (revisions.length > 0) {
      Arrays.sort(revisions, new Comparator<VcsFileRevision>() {
        public int compare(final VcsFileRevision o1, final VcsFileRevision o2) {
          return o1.getRevisionNumber().compareTo(o2.getRevisionNumber());
        }
      });

      for (VcsFileRevision revision : revisions) {
        if (! myHistorySession.isContentAvailable(revision)) {
          return null;
        }
      }

      final ContentRevision startRevision = new LoadedContentRevision(myFilePath, revisions[0]);
      final ContentRevision endRevision = (revisions.length == 1) ? new CurrentContentRevision(myFilePath) :
                                          new LoadedContentRevision(myFilePath, revisions[revisions.length - 1]);

      return new Change[]{new Change(startRevision, endRevision)};
    }
    return null;
  }

  private static class LoadedContentRevision implements ContentRevision {
    private final FilePath myFile;
    private final VcsFileRevision myRevision;

    private LoadedContentRevision(final FilePath file, final VcsFileRevision revision) {
      myFile = file;
      myRevision = revision;
    }

    public String getContent() throws VcsException {
      myRevision.loadContent();
      try {
        return LoadTextUtil.getTextByBinaryPresentation(myRevision.getContent(), myFile.getVirtualFile(), false).toString();
      }
      catch (IOException e) {
        throw new VcsException(VcsBundle.message("message.text.cannot.load.revision", e.getLocalizedMessage()));
      }
    }

    @NotNull
    public FilePath getFile() {
      return myFile;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return myRevision.getRevisionNumber();
    }
  }

  private VirtualFile createVirtualFileForRevision(VcsFileRevision revision) {
    if (!myRevisionToVirtualFile.containsKey(revision)) {
      myRevisionToVirtualFile.put(revision, new VcsVirtualFile(myFilePath.getPath(), revision, VcsFileSystem.getInstance()));
    }
    return myRevisionToVirtualFile.get(revision);
  }

  private List<TreeNodeOnVcsRevision> getSelection() {
    //noinspection unchecked
    return myDualView.getSelection();
  }

  @Nullable
  private VcsFileRevision getFirstSelectedRevision() {
    List selection = getSelection();
    if (selection.isEmpty()) return null;
    return ((TreeNodeOnVcsRevision)selection.get(0)).myRevision;
  }

  public VcsFileRevision[] getSelectedRevisions() {
    List<TreeNodeOnVcsRevision> selection = getSelection();
    VcsFileRevision[] result = new VcsFileRevision[selection.size()];
    for(int i=0; i<selection.size(); i++) {
      result [i] = selection.get(i).myRevision;
    }
    return result;
  }

  @Nullable
  private VcsFileRevision getSelectedRevision(int index) {
    List selection = getSelection();
    if (selection.isEmpty()) return null;
    return ((TreeNodeOnVcsRevision)selection.get(index)).myRevision;
  }

  static class TreeNodeOnVcsRevision extends DefaultMutableTreeNode implements VcsFileRevision, DualTreeElement {
    private final VcsFileRevision myRevision;

    public TreeNodeOnVcsRevision(VcsFileRevision revision, List<TreeItem<VcsFileRevision>> roots) {
      myRevision = revision == null ? VcsFileRevision.NULL : revision;
      for (final TreeItem<VcsFileRevision> root : roots) {
        add(new TreeNodeOnVcsRevision(root.getData(), root.getChildren()));
      }
    }

    public String getAuthor() {
      return myRevision.getAuthor();
    }

    public String getCommitMessage() {
      return myRevision.getCommitMessage();
    }

    public void loadContent() throws VcsException {
      myRevision.loadContent();
    }

    public VcsRevisionNumber getRevisionNumber() {
      return myRevision.getRevisionNumber();
    }

    public Date getRevisionDate() {
      return myRevision.getRevisionDate();
    }

    public String getBranchName() {
      return myRevision.getBranchName();
    }

    public byte[] getContent() throws IOException {
      return myRevision.getContent();
    }

    public String toString() {
      return getRevisionNumber().asString();
    }

    public boolean shouldBeInTheFlatView() {
      return myRevision != VcsFileRevision.NULL;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TreeNodeOnVcsRevision that = (TreeNodeOnVcsRevision)o;

      if (myRevision != null ? !myRevision.getRevisionNumber().equals(that.myRevision.getRevisionNumber()) : that.myRevision != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myRevision != null ? myRevision.getRevisionNumber().hashCode() : 0;
    }
  }

  protected void dispose() {
    super.dispose();
    myDualView.dispose();
    myUpdateAlarm.dispose();
  }

  abstract class AbstractActionForSomeSelection extends AnAction implements DumbAware {
    private final int mySuitableSelectedElements;
    private final FileHistoryPanelImpl mySelectionProvider;

    public AbstractActionForSomeSelection(String name,
                                          String description,
                                          @NonNls String iconName,
                                          int suitableSelectionSize,
                                          FileHistoryPanelImpl tableProvider) {
      super(name, description, IconLoader.getIcon("/actions/" + iconName + ".png"));
      mySuitableSelectedElements = suitableSelectionSize;
      mySelectionProvider = tableProvider;
    }

    protected abstract void actionPerformed();

    public boolean isEnabled() {
      return mySelectionProvider.getSelection().size() == mySuitableSelectedElements;
    }

    public void actionPerformed(AnActionEvent e) {
      if (!isEnabled()) return;
      actionPerformed();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setVisible(true);
      presentation.setEnabled(isEnabled());
    }
  }

  abstract static class VcsColumnInfo<T extends Comparable> extends DualViewColumnInfo<VcsFileRevision, String>
    implements Comparator<VcsFileRevision> {
    public VcsColumnInfo(String name) {
      super(name);
    }

    protected abstract T getDataOf(VcsFileRevision o);

    public Comparator<VcsFileRevision> getComparator() {
      return this;
    }

    public String valueOf(VcsFileRevision object) {
      T result = getDataOf(object);
      return result == null ? "" : result.toString();
    }

    public int compare(VcsFileRevision o1, VcsFileRevision o2) {
      return compareObjects(getDataOf(o1), getDataOf(o2));
    }

    private int compareObjects(Comparable data1, Comparable data2) {
      if (data1 == data2) return 0;
      if (data1 == null) return -1;
      if (data2 == null) return 1;
      return data1.compareTo(data2);
    }

    public boolean shouldBeShownIsTheTree() {
      return true;
    }

    public boolean shouldBeShownIsTheTable() {
      return true;
    }

  }

  private static class MyColumnWrapper<T> extends DualViewColumnInfo<TreeNodeOnVcsRevision, Object> {
    private final ColumnInfo<VcsFileRevision, T> myBaseColumn;

    public Comparator<TreeNodeOnVcsRevision> getComparator() {
      final Comparator comparator = myBaseColumn.getComparator();
      if (comparator == null) return null;
      return new Comparator<TreeNodeOnVcsRevision>() {
        public int compare(TreeNodeOnVcsRevision o1, TreeNodeOnVcsRevision o2) {
          if (o1 == null) return -1;
          if (o2 == null) return 1;
          VcsFileRevision revision1 = o1.myRevision;
          VcsFileRevision revision2 = o2.myRevision;
          if (revision1 == null) return -1;
          if (revision2 == null) return 1;
          return comparator.compare(revision1, revision2);
        }
      };
    }

    public String getName() {
      return myBaseColumn.getName();
    }

    public Class getColumnClass() {
      return myBaseColumn.getColumnClass();
    }

    public boolean isCellEditable(TreeNodeOnVcsRevision o) {
      return myBaseColumn.isCellEditable(o.myRevision);
    }

    public void setValue(TreeNodeOnVcsRevision o, Object aValue) {
      //noinspection unchecked
      myBaseColumn.setValue(o.myRevision, (T)aValue);
    }

    public TableCellRenderer getRenderer(TreeNodeOnVcsRevision p0) {
      return myBaseColumn.getRenderer(p0.myRevision);
    }

    public TableCellEditor getEditor(TreeNodeOnVcsRevision item) {
      return myBaseColumn.getEditor(item.myRevision);
    }

    public String getMaxStringValue() {
      return myBaseColumn.getMaxStringValue();
    }

    public int getAdditionalWidth() {
      return myBaseColumn.getAdditionalWidth();
    }

    public int getWidth(JTable table) {
      return myBaseColumn.getWidth(table);
    }

    public void setName(String s) {
      myBaseColumn.setName(s);
    }

    public MyColumnWrapper(ColumnInfo<VcsFileRevision, T> additionalColunm) {
      super(additionalColunm.getName());
      myBaseColumn = additionalColunm;
    }

    public boolean shouldBeShownIsTheTree() {
      return true;
    }

    public boolean shouldBeShownIsTheTable() {
      return true;
    }

    public Object valueOf(TreeNodeOnVcsRevision o) {
      return myBaseColumn.valueOf(o.myRevision);
    }

    public ColumnInfo getOriginalColumn() {
      return myBaseColumn;
    }
  }

  private VirtualFile getVirtualFile() {
    return myFilePath.getVirtualFile();
  }

  private VirtualFile getVirtualParent() {
    return myFilePath.getVirtualFileParent();
  }


  private class MyTreeCellRenderer implements TreeCellRenderer {
    private final TreeCellRenderer myDefaultCellRenderer;
    private final Getter<VcsHistorySession> myHistorySession;

    public MyTreeCellRenderer(final TreeCellRenderer defaultCellRenderer, final Getter<VcsHistorySession> historySession) {
      myDefaultCellRenderer = defaultCellRenderer;
      myHistorySession = historySession;
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      Component result = myDefaultCellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

      TreePath path = tree.getPathForRow(row);
      if (path == null) return result;
      VcsFileRevision revision = row >= 0 ? (VcsFileRevision)path.getLastPathComponent() : null;

      if (revision != null) {

        if (myHistorySession.get().isCurrentRevision(revision.getRevisionNumber())) {
          makeBold(result);
        }
        if (!selected && myHistorySession.get().isCurrentRevision(revision.getRevisionNumber())) {
          result.setBackground(new Color(188, 227, 231));
          ((JComponent)result).setOpaque(false);
        }
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
    private final Getter<VcsHistorySession> myHistorySession;

    public MyCellWrapper(final Getter<VcsHistorySession> historySession) {
      myHistorySession = historySession;
    }

    public void wrap(Component component,
                     JTable table,
                     Object value,
                     boolean isSelected,
                     boolean hasFocus,
                     int row,
                     int column,
                     Object treeNode) {
      VcsFileRevision revision = (VcsFileRevision)treeNode;
      if (revision == null) return;
      if (myHistorySession.get().isCurrentRevision(revision.getRevisionNumber())) {
        makeBold(component);
      }
    }
  }

  private class RefreshFileHistoryAction extends AnAction implements DumbAware {
    public RefreshFileHistoryAction() {
      super(VcsBundle.message("action.name.refresh"), VcsBundle.message("action.desctiption.refresh"), IconLoader.getIcon("/actions/sync.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      if (myInRefresh) return;
      refreshImpl();
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(! myInRefresh);
    }
  }

  private void refreshRevisionsOrder() {
    final List<VcsFileRevision> list = myHistorySession.getRevisionList();
    myRevisionsOrder.clear();

    int cnt = 0;
    for (VcsFileRevision revision : list) {
      myRevisionsOrder.put(revision.getRevisionNumber(), cnt);
      ++ cnt;
    }
  }
}

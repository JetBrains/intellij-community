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
package com.intellij.openapi.vcs.history.impl;

import com.intellij.diff.Block;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.LoadingDiffRequest;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.diff.requests.NoDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.IntPair;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

public class VcsSelectionHistoryDialog extends FrameWrapper implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.history.impl.VcsHistoryDialog");

  private static final VcsRevisionNumber LOCAL_REVISION_NUMBER = new VcsRevisionNumber() {
    @Override
    public String asString() {
      return "Local Changes";
    }

    @Override
    public int compareTo(@NotNull VcsRevisionNumber vcsRevisionNumber) {
      return 0;
    }

    @Override
    public String toString() {
      return asString();
    }
  };

  private static final float DIFF_SPLITTER_PROPORTION = 0.5f;
  private static final float COMMENTS_SPLITTER_PROPORTION = 0.8f;
  private static final String DIFF_SPLITTER_PROPORTION_KEY = "file.history.selection.diff.splitter.proportion";
  private static final String COMMENTS_SPLITTER_PROPORTION_KEY = "file.history.selection.comments.splitter.proportion";

  private static final Block EMPTY_BLOCK = new Block("", 0, 0);

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final AbstractVcs myActiveVcs;
  @NonNls private final String myHelpId;

  private final List<VcsFileRevision> myRevisions = new ArrayList<>();
  private final CurrentRevision myLocalRevision;

  private final ListTableModel<VcsFileRevision> myListModel;
  private final TableView<VcsFileRevision> myList;

  private final Splitter mySplitter;
  private final DiffRequestPanel myDiffPanel;
  private final JCheckBox myChangesOnlyCheckBox = new JCheckBox(VcsBundle.message("checkbox.show.changed.revisions.only"));
  private final JLabel myStatusLabel = new JBLabel();
  private final AnimatedIcon myStatusSpinner = new AsyncProcessIcon("VcsSelectionHistoryDialog");
  private final JEditorPane myComments;

  @NotNull private final MergingUpdateQueue myUpdateQueue;
  @NotNull private final BlockLoader myBlockLoader;

  private boolean myIsDuringUpdate = false;
  private boolean myIsDisposed = false;

  public VcsSelectionHistoryDialog(@NotNull Project project,
                                   @NotNull VirtualFile file,
                                   @NotNull Document document,
                                   @NotNull VcsHistoryProvider vcsHistoryProvider,
                                   @NotNull VcsHistorySession session,
                                   @NotNull AbstractVcs vcs,
                                   int selectionStart,
                                   int selectionEnd,
                                   @NotNull String title) {
    super(project);
    myProject = project;
    myFile = file;
    myActiveVcs = vcs;
    myHelpId = notNull(vcsHistoryProvider.getHelpId(), "reference.dialogs.vcs.selection.history");

    myComments = new JEditorPane(UIUtil.HTML_MIME, "");
    myComments.setPreferredSize(new JBDimension(150, 100));
    myComments.setEditable(false);
    myComments.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    JRootPane rootPane = ((RootPaneContainer)getFrame()).getRootPane();
    final VcsDependentHistoryComponents components = vcsHistoryProvider.getUICustomization(session, rootPane);

    ColumnInfo[] defaultColumns = new ColumnInfo[]{
      new FileHistoryPanelImpl.RevisionColumnInfo(null),
      new FileHistoryPanelImpl.DateColumnInfo(),
      new FileHistoryPanelImpl.AuthorColumnInfo(),
      new FileHistoryPanelImpl.MessageColumnInfo(project)};
    ColumnInfo[] additionalColumns = notNull(components.getColumns(), ColumnInfo.EMPTY_ARRAY);
    myListModel = new ListTableModel<>(ArrayUtil.mergeArrays(defaultColumns, additionalColumns));
    myListModel.setSortable(false);
    myList = new TableView<>(myListModel);
    new TableLinkMouseListener().installOn(myList);

    myList.getEmptyText().setText(VcsBundle.message("history.empty"));

    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, getFrame());
    myUpdateQueue = new MergingUpdateQueue("VcsSelectionHistoryDialog", 300, true, myList, this);

    myLocalRevision = new CurrentRevision(file, LOCAL_REVISION_NUMBER);
    myRevisions.add(myLocalRevision);
    myRevisions.addAll(session.getRevisionList());

    mySplitter = new JBSplitter(true, DIFF_SPLITTER_PROPORTION_KEY, DIFF_SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(myDiffPanel.getComponent());
    mySplitter.setSecondComponent(createBottomPanel(components.getDetailsComponent()));

    final ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final VcsFileRevision revision;
        if (myList.getSelectedRowCount() == 1 && !myList.isEmpty()) {
          revision = myList.getItems().get(myList.getSelectedRow());
          String message = IssueLinkHtmlRenderer.formatTextIntoHtml(myProject, revision.getCommitMessage());
          myComments.setText(message);
          myComments.setCaretPosition(0);
        }
        else {
          revision = null;
          myComments.setText("");
        }
        if (components.getRevisionListener() != null) {
          components.getRevisionListener().consume(revision);
        }
        updateDiff();
      }
    };
    myList.getSelectionModel().addListSelectionListener(selectionListener);

    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);
    myChangesOnlyCheckBox.setSelected(configuration.SHOW_ONLY_CHANGED_IN_SELECTION_DIFF);
    myChangesOnlyCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        configuration.SHOW_ONLY_CHANGED_IN_SELECTION_DIFF = myChangesOnlyCheckBox.isSelected();
        updateRevisionsList();
      }
    });

    final DefaultActionGroup popupActions = new DefaultActionGroup();
    popupActions.add(new MyDiffAction());
    popupActions.add(new MyDiffAfterWithLocalAction());
    popupActions.add(ShowAllAffectedGenericAction.getInstance());
    popupActions.add(ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER));
    PopupHandler.installPopupHandler(myList, popupActions, ActionPlaces.UPDATE_POPUP, ActionManager.getInstance());

    for (AnAction action : popupActions.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), mySplitter);
    }

    setTitle(title);
    setComponent(mySplitter);
    setPreferredFocusedComponent(myList);
    setDimensionKey("VCS.FileHistoryDialog");
    closeOnEsc();


    myBlockLoader = new BlockLoader(myRevisions, myFile, document, selectionStart, selectionEnd) {
      @Override
      protected void notifyError(@NotNull VcsException e) {
        SwingUtilities.invokeLater(() -> {
          VcsSelectionHistoryDialog dialog = VcsSelectionHistoryDialog.this;
          if (dialog.isDisposed() || !dialog.getFrame().isShowing()) return;
          PopupUtil.showBalloonForComponent(mySplitter, canNoLoadMessage(e), MessageType.ERROR, true, myProject);
        });
      }

      @Override
      protected void notifyUpdate() {
        myUpdateQueue.queue(new Update(this) {
          @Override
          public void run() {
            updateStatusPanel();
            updateRevisionsList();
          }
        });
      }
    };
    myBlockLoader.start(this);

    updateRevisionsList();
    if (myList.getRowCount() != 0) myList.getSelectionModel().setSelectionInterval(0, 0);
  }

  @NotNull
  private static String canNoLoadMessage(@Nullable VcsException e) {
    return "Can not load revision contents" + (e != null ? ": " + e.getMessage() : "");
  }

  private void updateRevisionsList() {
    if (myIsDuringUpdate) return;
    try {
      myIsDuringUpdate = true;

      List<VcsFileRevision> newItems;
      if (myChangesOnlyCheckBox.isSelected()) {
        newItems = filteredRevisions();
      }
      else {
        newItems = myRevisions;
      }

      IntPair range = getSelectedRevisionsRange();
      List<VcsFileRevision> oldSelection = myRevisions.subList(range.val1, range.val2);

      myListModel.setItems(newItems);

      myList.setSelection(oldSelection);
      if (myList.getSelectedRowCount() == 0) {
        int index = getNearestVisibleRevision(ContainerUtil.getFirstItem(oldSelection));
        if (myList.getRowCount() != 0) myList.getSelectionModel().setSelectionInterval(index, index);
      }
    }
    finally {
      myIsDuringUpdate = false;
    }

    updateDiff();
  }

  private void updateStatusPanel() {
    BlockData data = myBlockLoader.getLoadedData();

    if (data.isLoading()) {
      VcsFileRevision revision = data.getCurrentLoadingRevision();
      String loadingString = revision != null
                             ? String.format("Loading revision <tt>%s</tt>...", VcsUtil.getShortRevisionString(revision.getRevisionNumber()))
                             : "Loading...";
      myStatusLabel.setText(String.format("<html>%s (%s/%s)</html>", loadingString, data.myBlocks.size(), myRevisions.size()));

      myStatusSpinner.resume();
      myStatusSpinner.setVisible(true);
    }
    else {
      myStatusLabel.setText("");
      myStatusSpinner.suspend();
      myStatusSpinner.setVisible(false);
    }
  }

  @NotNull
  private IntPair getSelectedRevisionsRange() {
    List<VcsFileRevision> selection = myList.getSelectedObjects();
    if (selection.isEmpty()) return new IntPair(0, 0);
    int startIndex = myRevisions.indexOf(ContainerUtil.getFirstItem(selection));
    int endIndex = myRevisions.indexOf(ContainerUtil.getLastItem(selection));
    return new IntPair(startIndex, endIndex + 1);
  }

  private int getNearestVisibleRevision(@Nullable VcsFileRevision anchor) {
    int anchorIndex = myRevisions.indexOf(anchor);
    if (anchorIndex == -1) return 0;

    for (int i = anchorIndex - 1; i > 0; i--) {
      int index = myListModel.indexOf(myRevisions.get(i));
      if (index != -1) return index;
    }
    return 0;
  }

  private List<VcsFileRevision> filteredRevisions() {
    ArrayList<VcsFileRevision> result = new ArrayList<>();
    BlockData data = myBlockLoader.getLoadedData();

    for (int i = 1; i < myRevisions.size(); i++) {
      Block block1 = data.getBlock(i - 1);
      Block block2 = data.getBlock(i);
      if (block1 == null || block2 == null) break;
      if (!block1.getLines().equals(block2.getLines())) {
        result.add(myRevisions.get(i - 1));
      }
      if (block2 == EMPTY_BLOCK) break;
    }

    int initialCommit = myRevisions.size() - 1;
    Block initialCommitBlock = data.getBlock(initialCommit);
    if (initialCommitBlock != null && initialCommitBlock != EMPTY_BLOCK) {
      result.add(myRevisions.get(initialCommit));
    }

    return result;
  }

  private void updateDiff() {
    if (myIsDisposed || myIsDuringUpdate) return;

    if (myList.getSelectedRowCount() == 0) {
      myDiffPanel.setRequest(NoDiffRequest.INSTANCE);
      return;
    }

    int count = myRevisions.size();
    IntPair range = getSelectedRevisionsRange();
    int revIndex1 = range.val2;
    int revIndex2 = range.val1;

    if (revIndex1 == count && revIndex2 == count) {
      myDiffPanel.setRequest(NoDiffRequest.INSTANCE);
      return;
    }

    BlockData blockData = myBlockLoader.getLoadedData();
    DiffContent content1 = createDiffContent(revIndex1, blockData);
    DiffContent content2 = createDiffContent(revIndex2, blockData);
    String title1 = createDiffContentTitle(revIndex1);
    String title2 = createDiffContentTitle(revIndex2);
    if (content1 != null && content2 != null) {
      myDiffPanel.setRequest(new SimpleDiffRequest(null, content1, content2, title1, title2), new IntPair(revIndex1, revIndex2));
      return;
    }

    if (blockData.isLoading()) {
      myDiffPanel.setRequest(new LoadingDiffRequest());
    }
    else {
      myDiffPanel.setRequest(new MessageDiffRequest(canNoLoadMessage(blockData.getException())));
    }
  }

  @Nullable
  private String createDiffContentTitle(int index) {
    if (index >= myRevisions.size()) return null;
    return VcsBundle.message("diff.content.title.revision.number", myRevisions.get(index).getRevisionNumber());
  }

  @Nullable
  private DiffContent createDiffContent(int index, @NotNull BlockData data) {
    if (index >= myRevisions.size()) return DiffContentFactory.getInstance().createEmpty();
    Block block = data.getBlock(index);
    if (block == null) return null;
    if (block == EMPTY_BLOCK) return DiffContentFactory.getInstance().createEmpty();
    DocumentContent documentContent = DiffContentFactory.getInstance().create(block.getBlockContent(), myFile.getFileType());
    documentContent.putUserData(DiffUserDataKeysEx.LINE_NUMBER_CONVERTOR, value -> value + block.getStart());
    return documentContent;
  }

  @Override
  public void dispose() {
    myIsDisposed = true;
    super.dispose();
  }

  private JComponent createBottomPanel(final JComponent addComp) {
    JBSplitter splitter = new JBSplitter(true, COMMENTS_SPLITTER_PROPORTION_KEY, COMMENTS_SPLITTER_PROPORTION);
    splitter.setDividerWidth(4);

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);

    JPanel statusPanel = new JPanel(new FlowLayout());
    statusPanel.add(myStatusSpinner);
    statusPanel.add(myStatusLabel);

    JPanel separatorPanel = new JPanel(new BorderLayout());
    separatorPanel.add(myChangesOnlyCheckBox, BorderLayout.WEST);
    separatorPanel.add(statusPanel, BorderLayout.EAST);

    tablePanel.add(separatorPanel, BorderLayout.NORTH);

    splitter.setFirstComponent(tablePanel);
    splitter.setSecondComponent(createComments(addComp));

    return splitter;
  }

  private JComponent createComments(final JComponent addComp) {
    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.add(new JLabel("Commit Message:"), BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(myComments), BorderLayout.CENTER);

    final Splitter splitter = new Splitter(false);
    splitter.setFirstComponent(panel);
    splitter.setSecondComponent(addComp);
    return splitter;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else if (VcsDataKeys.VCS_VIRTUAL_FILE.is(dataId)) {
      return myFile;
    }
    else if (VcsDataKeys.VCS_FILE_REVISION.is(dataId)) {
      VcsFileRevision selectedObject = myList.getSelectedObject();
      return selectedObject instanceof CurrentRevision ? null : selectedObject;
    }
    else if (VcsDataKeys.VCS_FILE_REVISIONS.is(dataId)) {
      List<VcsFileRevision> revisions = ContainerUtil.filter(myList.getSelectedObjects(), Conditions.notEqualTo(myLocalRevision));
      return ArrayUtil.toObjectArray(revisions, VcsFileRevision.class);
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      return myActiveVcs.getKeyInstanceMethod();
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    return null;
  }

  private class MyDiffAction extends DumbAwareAction {
    public MyDiffAction() {
      super(VcsBundle.message("action.name.compare"), VcsBundle.message("action.description.compare"), AllIcons.Actions.Diff);
      setShortcutSet(CommonShortcuts.getDiff());
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(myList.getSelectedRowCount() > 1 ||
                                     myList.getSelectedRowCount() == 1 && myList.getSelectedObject() != myLocalRevision);
    }

    public void actionPerformed(AnActionEvent e) {
      IntPair range = getSelectedRevisionsRange();

      VcsFileRevision beforeRevision = range.val2 < myRevisions.size() ? myRevisions.get(range.val2) : VcsFileRevision.NULL;
      VcsFileRevision afterRevision = myRevisions.get(range.val1);

      FilePath filePath = VcsUtil.getFilePath(myFile);

      if (range.val2 - range.val1 > 1) {
        getDiffHandler().showDiffForTwo(myProject, filePath, beforeRevision, afterRevision);
      }
      else {
        getDiffHandler().showDiffForOne(e, myProject, filePath, beforeRevision, afterRevision);
      }
    }
  }

  private class MyDiffAfterWithLocalAction extends DumbAwareAction {
    public MyDiffAfterWithLocalAction() {
      ActionUtil.copyFrom(this, "Vcs.ShowDiffWithLocal");
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(myList.getSelectedRowCount() == 1 && myList.getSelectedObject() != myLocalRevision);
    }

    public void actionPerformed(AnActionEvent e) {
      VcsFileRevision revision = myList.getSelectedObject();
      if (revision == null) return;

      FilePath filePath = VcsUtil.getFilePath(myFile);

      getDiffHandler().showDiffForTwo(myProject, filePath, revision, myLocalRevision);
    }
  }

  @NotNull
  private DiffFromHistoryHandler getDiffHandler() {
    VcsHistoryProvider historyProvider = myActiveVcs.getVcsHistoryProvider();
    DiffFromHistoryHandler handler = historyProvider != null ? historyProvider.getHistoryDiffHandler() : null;
    return handler != null ? handler : new StandardDiffFromHistoryHandler();
  }

  private abstract static class BlockLoader {
    @NotNull private final Object LOCK = new Object();

    @NotNull private final List<VcsFileRevision> myRevisions;
    @NotNull private final Charset myCharset;

    @NotNull private final List<Block> myBlocks = new ArrayList<>();
    @Nullable private VcsException myException;
    private boolean myIsLoading = true;
    private VcsFileRevision myCurrentLoadingRevision;

    public BlockLoader(@NotNull List<VcsFileRevision> revisions,
                       @NotNull VirtualFile file,
                       @NotNull Document document,
                       int selectionStart,
                       int selectionEnd) {
      myRevisions = revisions;
      myCharset = file.getCharset();

      String[] lastContent = Block.tokenize(document.getText());
      myBlocks.add(new Block(lastContent, selectionStart, selectionEnd + 1));
    }

    @NotNull
    public BlockData getLoadedData() {
      synchronized (LOCK) {
        return new BlockData(myIsLoading, new ArrayList<>(myBlocks), myException, myCurrentLoadingRevision);
      }
    }

    public void start(@NotNull Disposable disposable) {
      BackgroundTaskUtil.executeOnPooledThread(disposable, () -> {
        try {
          // first block is loaded in constructor
          for (int index = 1; index < myRevisions.size(); index++) {
            ProgressManager.checkCanceled();

            Block block = myBlocks.get(index - 1);
            VcsFileRevision revision = myRevisions.get(index);

            synchronized (LOCK) {
              myCurrentLoadingRevision = revision;
            }
            notifyUpdate();

            Block previousBlock = createBlock(block, revision);

            synchronized (LOCK) {
              myBlocks.add(previousBlock);
            }
            notifyUpdate();
          }
        }
        catch (VcsException e) {
          synchronized (LOCK) {
            myException = e;
          }
          notifyError(e);
        }
        finally {
          synchronized (LOCK) {
            myIsLoading = false;
            myCurrentLoadingRevision = null;
          }
          notifyUpdate();
        }
      });
    }

    @CalledInBackground
    protected abstract void notifyError(@NotNull VcsException e);

    @CalledInBackground
    protected abstract void notifyUpdate();

    @NotNull
    private Block createBlock(@NotNull Block block, @NotNull VcsFileRevision revision) throws VcsException {
      if (block == EMPTY_BLOCK) return EMPTY_BLOCK;

      String revisionContent = loadContents(revision);

      Block newBlock = block.createPreviousBlock(revisionContent);
      return newBlock.getStart() != newBlock.getEnd() ? newBlock : EMPTY_BLOCK;
    }

    @NotNull
    private String loadContents(@NotNull VcsFileRevision revision) throws VcsException {
      try {
        byte[] bytes = revision.loadContent();
        return new String(bytes, myCharset);
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
    }
  }

  private static class BlockData {
    private final boolean myIsLoading;
    @NotNull private final List<Block> myBlocks;
    @Nullable private final VcsException myException;
    @Nullable private final VcsFileRevision myCurrentLoadingRevision;

    public BlockData(boolean isLoading,
                     @NotNull List<Block> blocks,
                     @Nullable VcsException exception,
                     @Nullable VcsFileRevision currentLoadingRevision) {
      myIsLoading = isLoading;
      myBlocks = blocks;
      myException = exception;
      myCurrentLoadingRevision = currentLoadingRevision;
    }

    public boolean isLoading() {
      return myIsLoading;
    }

    @Nullable
    public VcsException getException() {
      return myException;
    }

    @Nullable
    public VcsFileRevision getCurrentLoadingRevision() {
      return myCurrentLoadingRevision;
    }

    @Nullable
    public Block getBlock(int index) {
      if (myBlocks.size() <= index) return null;
      return myBlocks.get(index);
    }
  }
}

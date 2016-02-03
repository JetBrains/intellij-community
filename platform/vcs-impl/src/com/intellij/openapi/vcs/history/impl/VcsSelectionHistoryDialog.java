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
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.diff.requests.NoDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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

  private static final ColumnInfo REVISION = new ColumnInfo(VcsBundle.message("column.name.revision.version")) {
    @Override
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getRevisionNumber();
    }
  };

  private static final ColumnInfo DATE = new ColumnInfo(VcsBundle.message("column.name.revision.list.date")) {
    @Override
    public Object valueOf(Object object) {
      Date date = ((VcsFileRevision)object).getRevisionDate();
      if (date == null) return "";
      return DateFormatUtil.formatPrettyDateTime(date);
    }
  };

  private static final ColumnInfo MESSAGE = new ColumnInfo(VcsBundle.message("column.name.revision.list.message")) {
    @Override
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getCommitMessage();
    }
  };

  private static final ColumnInfo AUTHOR = new ColumnInfo(VcsBundle.message("column.name.revision.list.author")) {
    @Override
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getAuthor();
    }
  };
  private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{REVISION, DATE, AUTHOR, MESSAGE};

  private static final float DIFF_SPLITTER_PROPORTION = 0.5f;
  private static final float COMMENTS_SPLITTER_PROPORTION = 0.8f;
  private static final String DIFF_SPLITTER_PROPORTION_KEY = "file.history.selection.diff.splitter.proportion";
  private static final String COMMENTS_SPLITTER_PROPORTION_KEY = "file.history.selection.comments.splitter.proportion";

  private static final Block EMPTY_BLOCK = new Block("", 0, 0);

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final Editor myEditor;
  @NotNull private final AbstractVcs myActiveVcs;
  @NotNull private final CachedRevisionsContents myCachedContents;
  private final int mySelectionStart;
  private final int mySelectionEnd;
  @NonNls private final String myHelpId;

  private final List<Block> myBlocks = new ArrayList<Block>();
  private final List<VcsFileRevision> myRevisions = new ArrayList<VcsFileRevision>();

  private final ListTableModel<VcsFileRevision> myListModel;
  private final TableView<VcsFileRevision> myList;

  private final Splitter mySplitter;
  private final DiffRequestPanel myDiffPanel;
  private final JCheckBox myChangesOnlyCheckBox = new JCheckBox(VcsBundle.message("checkbox.show.changed.revisions.only"));
  private final JTextArea myComments = new JTextArea();

  private boolean myIsDuringUpdate = false;
  private boolean myIsDisposed = false;

  public VcsSelectionHistoryDialog(@NotNull Project project,
                                   @NotNull VirtualFile file,
                                   @NotNull Editor editor,
                                   @NotNull VcsHistoryProvider vcsHistoryProvider,
                                   @NotNull VcsHistorySession session,
                                   @NotNull AbstractVcs vcs,
                                   int selectionStart,
                                   int selectionEnd,
                                   @NotNull String title,
                                   @NotNull CachedRevisionsContents cachedContents) {
    super(project);
    myProject = project;
    myFile = file;
    myEditor = editor;
    myActiveVcs = vcs;
    myCachedContents = cachedContents;
    mySelectionStart = selectionStart;
    mySelectionEnd = selectionEnd;
    myHelpId = notNull(vcsHistoryProvider.getHelpId(), "reference.dialogs.vcs.selection.history");

    JRootPane rootPane = ((RootPaneContainer)getFrame()).getRootPane();
    final VcsDependentHistoryComponents components = vcsHistoryProvider.getUICustomization(session, rootPane);

    ColumnInfo[] additionalColumns = notNull(components.getColumns(), ColumnInfo.EMPTY_ARRAY);
    myListModel = new ListTableModel<VcsFileRevision>(ArrayUtil.mergeArrays(COLUMNS, additionalColumns));
    myListModel.setSortable(false);
    myList = new TableView<VcsFileRevision>(myListModel);

    myList.getEmptyText().setText(VcsBundle.message("history.empty"));

    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, getFrame());

    myRevisions.add(new CurrentRevision(file, LOCAL_REVISION_NUMBER));
    myRevisions.addAll(session.getRevisionList());

    myBlocks.addAll(Collections.<Block>nCopies(myRevisions.size(), null));

    mySplitter = new JBSplitter(true, DIFF_SPLITTER_PROPORTION_KEY, DIFF_SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(myDiffPanel.getComponent());
    mySplitter.setSecondComponent(createBottomPanel(components.getDetailsComponent()));

    final ListSelectionListener selectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final VcsFileRevision revision;
        if (myList.getSelectedRowCount() == 1 && !myList.isEmpty()) {
          revision = myList.getItems().get(myList.getSelectedRow());
          myComments.setText(revision.getCommitMessage());
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

    updateRevisionsList();
    myList.getSelectionModel().setSelectionInterval(0, 0);

    final DefaultActionGroup popupActions = new DefaultActionGroup();
    popupActions.add(new MyDiffAction());
    popupActions.add(new MyDiffLocalAction());
    popupActions.add(ShowAllAffectedGenericAction.getInstance());
    popupActions.add(ActionManager.getInstance().getAction(VcsActions.ACTION_COPY_REVISION_NUMBER));
    myList.addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UPDATE_POPUP, popupActions);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    setTitle(title);
    setComponent(mySplitter);
    setPreferredFocusedComponent(myList);
    setDimensionKey("VCS.FileHistoryDialog");
    closeOnEsc();
  }

  private void canNotLoadRevisionMessage(final VcsException e) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!VcsSelectionHistoryDialog.this.getFrame().isShowing()) return;
        PopupUtil.showBalloonForComponent(VcsSelectionHistoryDialog.this.getFrame(), canNoLoadMessage(e), MessageType.ERROR, true, myProject);
      }
    });
  }

  private String canNoLoadMessage(VcsException e) {
    return "Can not load revision contents: " + e.getMessage();
  }

  protected String getContentOf(VcsFileRevision revision) throws VcsException {
    return myCachedContents.getContentOf(revision);
  }

  private void loadContentsFor(final VcsFileRevision[] revisions) throws VcsException {
    myCachedContents.loadContentsFor(revisions);
  }

  private void updateRevisionsList() {
    if (myIsDuringUpdate) return;
    try {
      myIsDuringUpdate = true;

      List<VcsFileRevision> newItems;
      if (myChangesOnlyCheckBox.isSelected()) {
        try {
          loadContentsFor(myRevisions.toArray(new VcsFileRevision[myRevisions.size()]));
          newItems = filteredRevisions();
        }
        catch (final VcsException e) {
          // todo test it, always exception
          canNotLoadRevisionMessage(e);
          return;
        }
      }
      else {
        newItems = myRevisions;
      }

      List<VcsFileRevision> oldSelection = getSelectedRevisions();

      myListModel.setItems(newItems);

      myList.setSelection(oldSelection);
      if (myList.getSelectedRowCount() == 0) {
        int index = getNearestVisibleRevision(ContainerUtil.getFirstItem(oldSelection));
        myList.getSelectionModel().setSelectionInterval(index, index);
      }
    }
    finally {
      myIsDuringUpdate = false;
    }

    updateDiff();
  }

  @NotNull
  private List<VcsFileRevision> getSelectedRevisions() {
    int minIndex = myList.getSelectionModel().getMinSelectionIndex();
    int maxIndex = myList.getSelectionModel().getMaxSelectionIndex();
    VcsFileRevision minRevision = minIndex != -1 ? myList.getRow(minIndex) : null;
    VcsFileRevision maxRevision = maxIndex != -1 ? myList.getRow(maxIndex) : null;
    int startIndex = myRevisions.indexOf(minRevision);
    int endIndex = myRevisions.indexOf(maxRevision);

    return startIndex != -1 && endIndex != -1 ?
           myRevisions.subList(startIndex, endIndex + 1) :
           Collections.<VcsFileRevision>emptyList();
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

  private List<VcsFileRevision> filteredRevisions() throws VcsException {
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();

    int firstRevision;
    for (firstRevision = myRevisions.size() - 1; firstRevision > 0; firstRevision--) {
      if (getBlock(firstRevision) != EMPTY_BLOCK) break;
    }

    result.add(myRevisions.get(firstRevision));

    for (int i = firstRevision - 1; i >= 0; i--) {
      Block block1 = getBlock(i + 1);
      Block block2 = getBlock(i);
      if (block1.getLines().equals(block2.getLines())) continue;
      result.add(myRevisions.get(i));
    }

    Collections.reverse(result);
    return result;
  }

  private void updateDiff() {
    if (myIsDisposed || myIsDuringUpdate) return;
    List<VcsFileRevision> selected = myList.getSelectedObjects();
    if (selected.isEmpty()) {
      myDiffPanel.setRequest(NoDiffRequest.INSTANCE);
    }
    else if (selected.size() == 1) {
      VcsFileRevision revision = selected.get(0);
      int index = myRevisions.indexOf(revision);
      myDiffPanel.setRequest(createDiffRequest(index + 1, index));
    }
    else {
      VcsFileRevision revision1 = selected.get(0);
      VcsFileRevision revision2 = selected.get(selected.size() - 1);
      myDiffPanel.setRequest(createDiffRequest(myRevisions.indexOf(revision2) + 1, myRevisions.indexOf(revision1)));
    }
  }

  @NotNull
  private DiffRequest createDiffRequest(int revIndex1, int revIndex2) {
    try {
      int count = myRevisions.size();
      if (revIndex1 == count && revIndex2 == count) return NoDiffRequest.INSTANCE;

      DiffContent content1 = createDiffContent(revIndex1);
      DiffContent content2 = createDiffContent(revIndex2);
      String title1 = createDiffContentTitle(revIndex1);
      String title2 = createDiffContentTitle(revIndex2);
      return new SimpleDiffRequest(null, content1, content2, title1, title2);
    }
    catch (VcsException e) {
      return new MessageDiffRequest(canNoLoadMessage(e));
    }
  }

  @Nullable
  private String createDiffContentTitle(int index) {
    if (index >= myRevisions.size()) return null;
    return VcsBundle.message("diff.content.title.revision.number", myRevisions.get(index).getRevisionNumber());
  }

  @NotNull
  private DiffContent createDiffContent(int index) throws VcsException {
    if (index >= myRevisions.size()) return DiffContentFactory.getInstance().createEmpty();
    if (getBlock(index) == EMPTY_BLOCK) return DiffContentFactory.getInstance().createEmpty();
    return DiffContentFactory.getInstance().create(getBlock(index).getBlockContent(), myFile.getFileType());
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
    tablePanel.add(myChangesOnlyCheckBox, BorderLayout.NORTH);

    splitter.setFirstComponent(tablePanel);
    splitter.setSecondComponent(createComments(addComp));

    return splitter;
  }

  private JComponent createComments(final JComponent addComp) {
    final JLabel label = new JLabel("Commit Message:");

    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.add(label, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(myComments), BorderLayout.CENTER);

    myComments.setRows(5);
    myComments.setEditable(false);
    myComments.setLineWrap(true);

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
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.is(dataId)) {
      List<VcsFileRevision> objects = myList.getSelectedObjects();
      List<VcsRevisionNumber> revisionNumbers = ContainerUtil.mapNotNull(objects, new Function<VcsFileRevision, VcsRevisionNumber>() {
        @Override
        public VcsRevisionNumber fun(VcsFileRevision revision) {
          return revision instanceof CurrentRevision ? null : revision.getRevisionNumber();
        }
      });
      return ArrayUtil.toObjectArray(revisionNumbers, VcsRevisionNumber.class);
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      return myActiveVcs.getKeyInstanceMethod();
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    return null;
  }

  private void ensureBlocksCreated(int requiredIndex) throws VcsException {
    for (int i = 0; i <= requiredIndex; i++) {
      if (myBlocks.get(i) == null) {
        myBlocks.set(i, createBlock(i));
      }
    }
  }

  @NotNull
  private Block createBlock(int index) throws VcsException {
    if (index == 0) {
      return new Block(myEditor.getDocument().getText(), mySelectionStart, mySelectionEnd + 1);
    }

    Block previousBlock = myBlocks.get(index - 1);
    if (previousBlock == EMPTY_BLOCK) return EMPTY_BLOCK;

    String revisionContent = getContentOf(myRevisions.get(index));
    if (revisionContent == null) return EMPTY_BLOCK;

    Block newBlock = previousBlock.createPreviousBlock(revisionContent);
    return newBlock.getStart() != newBlock.getEnd() ? newBlock : EMPTY_BLOCK;
  }

  @NotNull
  private Block getBlock(int index) throws VcsException {
    if (myBlocks.get(index) == null) {
      ensureBlocksCreated(index);
    }
    return myBlocks.get(index);
  }

  private class MyDiffAction extends DumbAwareAction {
    public MyDiffAction() {
      super(VcsBundle.message("action.name.compare"), VcsBundle.message("action.description.compare"), AllIcons.Actions.Diff);
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(myList.getSelectedRowCount() > 1 ||
                                     myList.getSelectedRowCount() == 1 && myList.getSelectedRow() != 0);
    }

    public void actionPerformed(AnActionEvent e) {
      int minIndex = myList.getSelectionModel().getMinSelectionIndex();
      int maxIndex = myList.getSelectionModel().getMaxSelectionIndex();
      if (minIndex == -1 || maxIndex == -1) return;

      VcsFileRevision minRevision = myList.getRow(minIndex);
      VcsFileRevision maxRevision = myList.getRow(maxIndex);
      int startIndex = myRevisions.indexOf(minRevision);
      int endIndex = myRevisions.indexOf(maxRevision);
      if (startIndex == -1 || endIndex == -1) return;

      VcsFileRevision revision = myRevisions.get(startIndex);
      VcsFileRevision prevRevision = endIndex + 1 < myRevisions.size() ? myRevisions.get(endIndex + 1) : VcsFileRevision.NULL;
      FilePath filePath = VcsUtil.getFilePath(myFile);

      if (startIndex != endIndex) {
        getDiffHandler().showDiffForTwo(myProject, filePath, prevRevision, revision);
      }
      else {
        getDiffHandler().showDiffForOne(e, myProject, filePath, prevRevision, revision);
      }
    }
  }

  private class MyDiffLocalAction extends DumbAwareAction {
    public MyDiffLocalAction() {
      super(VcsBundle.message("show.diff.with.local.action.text"),
            VcsBundle.message("show.diff.with.local.action.description"),
            AllIcons.Actions.DiffWithCurrent);
    }

    public void update(final AnActionEvent e) {
      e.getPresentation().setEnabled(myList.getSelectedRowCount() == 1 && myList.getSelectedRow() != 0);
    }

    public void actionPerformed(AnActionEvent e) {
      VcsFileRevision revision = myList.getSelectedObject();
      if (revision == null) return;

      VcsFileRevision localRevision = myRevisions.get(0);
      FilePath filePath = VcsUtil.getFilePath(myFile);

      getDiffHandler().showDiffForTwo(myProject, filePath, revision, localRevision);
    }
  }

  @NotNull
  private DiffFromHistoryHandler getDiffHandler() {
    VcsHistoryProvider historyProvider = myActiveVcs.getVcsHistoryProvider();
    DiffFromHistoryHandler handler = historyProvider != null ? historyProvider.getHistoryDiffHandler() : null;
    return handler != null ? handler : new StandardDiffFromHistoryHandler();
  }
}

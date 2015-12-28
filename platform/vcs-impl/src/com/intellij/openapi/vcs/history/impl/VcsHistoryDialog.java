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

import com.intellij.diff.*;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.MessageDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class VcsHistoryDialog extends FrameWrapper implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.history.impl.VcsHistoryDialog");

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

  private static final int CURRENT = 0;

  private static final float DIFF_SPLITTER_PROPORTION = 0.5f;
  private static final float COMMENTS_SPLITTER_PROPORTION = 0.8f;
  private static final String DIFF_SPLITTER_PROPORTION_KEY = "file.history.selection.diff.splitter.proportion";
  private static final String COMMENTS_SPLITTER_PROPORTION_KEY = "file.history.selection.comments.splitter.proportion";


  private final Project myProject;
  private final VirtualFile myFile;
  private final Editor myEditor;
  private final AbstractVcs myActiveVcs;
  private final CachedRevisionsContents myCachedContents;
  private final int mySelectionStart;
  private final int mySelectionEnd;
  @NonNls private final String myHelpId;

  // todo equals???
  private final Map<VcsFileRevision, Block> myRevisionToContentMap = new HashMap<VcsFileRevision, Block>();
  private final List<VcsFileRevision> myRevisions = new ArrayList<VcsFileRevision>();

  private final ListTableModel<VcsFileRevision> myListModel;
  private final TableView<VcsFileRevision> myList;

  private final Splitter mySplitter;
  private final DiffRequestPanel myDiffPanel;
  private final JCheckBox myChangesOnlyCheckBox = new JCheckBox(VcsBundle.message("checkbox.show.changed.revisions.only"));
  private final JTextArea myComments = new JTextArea();

  private boolean myIsInLoading = false;
  private boolean myIsDisposed = false;

  public VcsHistoryDialog(Project project,
                          VirtualFile file,
                          Editor editor,
                          VcsHistoryProvider vcsHistoryProvider,
                          VcsHistorySession session,
                          AbstractVcs vcs,
                          int selectionStart,
                          int selectionEnd,
                          String title,
                          CachedRevisionsContents cachedContents) {
    super(project);
    myProject = project;
    myFile = file;
    myEditor = editor;
    myActiveVcs = vcs;
    myCachedContents = cachedContents;
    mySelectionStart = selectionStart;
    mySelectionEnd = selectionEnd;
    myHelpId = ObjectUtils.notNull(vcsHistoryProvider.getHelpId(), "reference.dialogs.vcs.selection.history");

    JRootPane rootPane = ((RootPaneContainer)getFrame()).getRootPane();
    final VcsDependentHistoryComponents components = vcsHistoryProvider.getUICustomization(session, rootPane);

    ColumnInfo[] additionalColumns = ObjectUtils.notNull(components.getColumns(), ColumnInfo.EMPTY_ARRAY);
    myListModel = new ListTableModel<VcsFileRevision>(ArrayUtil.mergeArrays(COLUMNS, additionalColumns));
    myListModel.setSortable(false);
    myList = new TableView<VcsFileRevision>(myListModel);

    myList.getEmptyText().setText(VcsBundle.message("history.empty"));

    myDiffPanel = DiffManager.getInstance().createRequestPanel(myProject, this, getFrame());

    final VcsRevisionNumber currentRevisionNumber = session.getCurrentRevisionNumber();
    if (currentRevisionNumber != null) {
      myRevisions.add(new CurrentRevision(file, currentRevisionNumber));
    }
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
        if (!VcsHistoryDialog.this.getFrame().isShowing()) return;
        PopupUtil.showBalloonForComponent(VcsHistoryDialog.this.getFrame(), canNoLoadMessage(e), MessageType.ERROR, true, myProject);
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
    if (myIsInLoading) return;

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

    myListModel.setItems(newItems);
    myList.getSelectionModel().setSelectionInterval(0, 0);
  }

  private List<VcsFileRevision> filteredRevisions() throws VcsException {
    ArrayList<VcsFileRevision> result = new ArrayList<VcsFileRevision>();
    VcsFileRevision nextRevision = myRevisions.get(myRevisions.size() - 1);
    result.add(nextRevision);
    for (int i = myRevisions.size() - 2; i >= 0; i--) {
      VcsFileRevision vcsFileRevision = myRevisions.get(i);
      if (getContentToShow(nextRevision).equals(getContentToShow(vcsFileRevision))) continue;
      result.add(vcsFileRevision);
      nextRevision = vcsFileRevision;
    }
    Collections.reverse(result);
    return result;
  }

  private void updateDiff() {
    if (myList.isEmpty()) return;
    int[] selectedIndices = myList.getSelectedRows();
    if (selectedIndices.length == 0) {
      updateDiff(CURRENT, CURRENT);
    }
    else if (selectedIndices.length == 1) {
      updateDiff(selectedIndices[0], CURRENT);
    }
    else {
      updateDiff(selectedIndices[selectedIndices.length - 1], selectedIndices[0]);
    }
  }

  private void updateDiff(int first, int second) {
    if (myIsDisposed || myIsInLoading) return;

    VcsFileRevision firstRev = myListModel.getRowValue(first);
    VcsFileRevision secondRev = myListModel.getRowValue(second);

    DiffRequest diffRequest = createDiffRequest(firstRev, secondRev);
    myDiffPanel.setRequest(diffRequest);
  }

  @NotNull
  private DiffRequest createDiffRequest(@NotNull VcsFileRevision firstRev, @NotNull VcsFileRevision secondRev) {
    try {
      DiffContent content1 = DiffContentFactory.getInstance().create(getContentToShow(firstRev), myFile.getFileType());
      DiffContent content2 = DiffContentFactory.getInstance().create(getContentToShow(secondRev), myFile.getFileType());

      String title1 = VcsBundle.message("diff.content.title.revision.number", firstRev.getRevisionNumber());
      String title2 = VcsBundle.message("diff.content.title.revision.number", secondRev.getRevisionNumber());

      return new SimpleDiffRequest(null, content1, content2, title1, title2);
    }
    catch (VcsException e) {
      return new MessageDiffRequest(canNoLoadMessage(e));
    }
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
      return myList.getSelectedObject();
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      return myActiveVcs.getKeyInstanceMethod();
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myHelpId;
    }
    return null;
  }

  private String getContentToShow(VcsFileRevision revision) throws VcsException {
    final Block block = getBlock(revision);
    if (block == null) return "";
    return block.getBlockContent();
  }

  @Nullable
  private Block getBlock(VcsFileRevision revision) throws VcsException {
    if (myRevisionToContentMap.containsKey(revision)) {
      return myRevisionToContentMap.get(revision);
    }

    final String revisionContent = getContentOf(revision);
    if (revisionContent == null) return null;

    int index = myRevisions.indexOf(revision);
    Block blockByIndex = getBlock(index);
    if (blockByIndex == null) return null;

    myRevisionToContentMap.put(revision, new FindBlock(revisionContent, blockByIndex).getBlockInThePrevVersion());
    return myRevisionToContentMap.get(revision);
  }

  private Block getBlock(int index) throws VcsException {
    return index > 0 ? getBlock(myRevisions.get(index - 1)) : new Block(myEditor.getDocument().getText(), mySelectionStart, mySelectionEnd);
  }
}

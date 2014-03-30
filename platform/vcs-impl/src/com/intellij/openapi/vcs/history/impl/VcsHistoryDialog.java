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
import com.intellij.diff.FindBlock;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.HashMap;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

public class VcsHistoryDialog extends DialogWrapper implements DataProvider {
  private final Editor myEditor;
  private final int mySelectionStart;
  private final int mySelectionEnd;

  // todo equals???
  private final Map<VcsFileRevision, Block> myRevisionToContentMap = new HashMap<VcsFileRevision, Block>();

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.history.impl.VcsHistoryDialog");
  private final AbstractVcs myActiveVcs;

  private final DiffPanel myDiffPanel;
  private final Project myProject;

  private static final ColumnInfo REVISION = new ColumnInfo(VcsBundle.message("column.name.revision.version")) {
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getRevisionNumber();
    }

  };

  private static final ColumnInfo DATE = new ColumnInfo(VcsBundle.message("column.name.revision.list.date")) {
    public Object valueOf(Object object) {
      Date date = ((VcsFileRevision)object).getRevisionDate();
      if (date == null) return "";
      return DateFormatUtil.formatPrettyDateTime(date);
    }

  };

  private static final ColumnInfo MESSAGE = new ColumnInfo(VcsBundle.message("column.name.revision.list.message")) {
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getCommitMessage();
    }

  };

  private static final ColumnInfo AUTHOR = new ColumnInfo(VcsBundle.message("column.name.revision.list.author")) {
    public Object valueOf(Object object) {
      return ((VcsFileRevision)object).getAuthor();
    }

  };

  private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{REVISION, DATE, AUTHOR, MESSAGE};

  private final TableView myList;
  protected final List<VcsFileRevision> myRevisions;
  private final Splitter mySplitter;
  private final VirtualFile myFile;
  private final JCheckBox myChangesOnlyCheckBox = new JCheckBox(VcsBundle.message("checkbox.show.changed.revisions.only"));
  private final CachedRevisionsContents myCachedContents;
  private final JTextArea myComments = new JTextArea();
  private static final int CURRENT = 0;
  private boolean myIsInLoading = false;
  @NonNls private final String myHelpId;
  private boolean myIsDisposed = false;
  private final FileType myContentFileType;

  public VcsHistoryDialog(Project project,
                          final VirtualFile file,
                          Editor editor, final VcsHistoryProvider vcsHistoryProvider,
                          VcsHistorySession session,
                          AbstractVcs vcs,
                          int selectionStart,
                          int selectionEnd,
                          final String title, final CachedRevisionsContents cachedContents){
    super(project, true);
    myProject = project;
    myEditor = editor;
    mySelectionStart = selectionStart;
    mySelectionEnd = selectionEnd;
    myCachedContents = cachedContents;
    setTitle(title);
    myActiveVcs = vcs;
    myRevisions = new ArrayList<VcsFileRevision>();
    myFile = file;
    String helpId = vcsHistoryProvider.getHelpId();
    myHelpId = helpId != null ? helpId : "reference.dialogs.vcs.selection.history";
    final VcsDependentHistoryComponents components = vcsHistoryProvider.getUICustomization(session, getRootPane());
    myList = new TableView(new ListTableModel(createColumns(components.getColumns())));
    ((SortableColumnModel)myList.getModel()).setSortable(false);

    myList.getEmptyText().setText(VcsBundle.message("history.empty"));

    myDiffPanel = DiffManager.getInstance().createDiffPanel(getWindow(), myProject,getDisposable(), null);
    myDiffPanel.setRequestFocus(false);

    myRevisions.addAll(session.getRevisionList());
    final VcsRevisionNumber currentRevisionNumber = session.getCurrentRevisionNumber();
    if (currentRevisionNumber != null) {
      myRevisions.add(new CurrentRevision(file, currentRevisionNumber));
    }
    Collections.sort(myRevisions, new Comparator<VcsFileRevision>() {
      public int compare(VcsFileRevision rev1, VcsFileRevision rev2){
        return VcsHistoryUtil.compare(rev1, rev2);
      }
    });
    Collections.reverse(myRevisions);

    myContentFileType = file.getFileType();

    final VcsConfiguration configuration = VcsConfiguration.getInstance(myProject);

    mySplitter = new Splitter(true, getVcsConfiguration().FILE_HISTORY_DIALOG_SPLITTER_PROPORTION);

    mySplitter.setFirstComponent(myDiffPanel.getComponent());
    mySplitter.setSecondComponent(createBottomPanel(components.getDetailsComponent()));

    mySplitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          getVcsConfiguration().FILE_HISTORY_DIALOG_SPLITTER_PROPORTION
          = ((Float)evt.getNewValue()).floatValue();
        }
      }
    });


    final ListSelectionListener selectionListener = new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final VcsFileRevision revision;
        if (myList.getSelectedRowCount() == 1 && !myList.isEmpty()) {
          revision = (VcsFileRevision)myList.getItems().get(myList.getSelectedRow());
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

    myChangesOnlyCheckBox.setSelected(configuration.SHOW_ONLY_CHANGED_IN_SELECTION_DIFF);
    try {
      updateRevisionsList();
    }
    catch (final VcsException e) {
      // todo test it, always exception
      canNotLoadRevisionMessage(e);
    }
    myChangesOnlyCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        configuration.SHOW_ONLY_CHANGED_IN_SELECTION_DIFF = myChangesOnlyCheckBox.isSelected();
        try {
          updateRevisionsList();
        }
        catch (VcsException e1) {
          canNotLoadRevisionMessage(e1);
        }
      }
    });

    init();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (! VcsHistoryDialog.this.isShowing()) return;
        myList.getSelectionModel().addSelectionInterval(0, 0);
      }
    });

    setTitle(VcsBundle.message("dialog.title.history.for.file", file.getName()));
  }

  private void canNotLoadRevisionMessage(final VcsException e) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (! VcsHistoryDialog.this.isShowing()) return;
        PopupUtil.showBalloonForComponent(VcsHistoryDialog.this.getRootPane(), canNoLoadMessage(e), MessageType.ERROR, true, myProject);
      }
    });
  }

  private String canNoLoadMessage(VcsException e) {
    return "Can not load revision contents: " + e.getMessage();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  public void show() {
    myList.getSelectionModel().setSelectionInterval(0, 0);
    super.show();
  }

  private static ColumnInfo[] createColumns(ColumnInfo[] additionalColumns) {
    if (additionalColumns == null) {
      return COLUMNS;
    }

    ColumnInfo[] result = new ColumnInfo[additionalColumns.length + COLUMNS.length];

    System.arraycopy(COLUMNS, 0, result, 0, COLUMNS.length);
    System.arraycopy(additionalColumns, 0, result, COLUMNS.length, additionalColumns.length);

    return result;
  }

  protected String getContentOf(VcsFileRevision revision) throws VcsException {
    return myCachedContents.getContentOf(revision);
  }

  private void loadContentsFor(final VcsFileRevision[] revisions) throws VcsException {
    myCachedContents.loadContentsFor(revisions);
  }

  private void updateRevisionsList() throws VcsException {
    if (myIsInLoading) return;
    if (myChangesOnlyCheckBox.isSelected()) {
      loadContentsFor(myRevisions.toArray(new VcsFileRevision[myRevisions.size()]));
      try {
        ((ListTableModel)myList.getModel()).setItems(filteredRevisions());
      }
      catch (FilesTooBigForDiffException e) {
        myChangesOnlyCheckBox.setEnabled(false);
        myChangesOnlyCheckBox.setSelected(false);
        setErrorText(e.getMessage());
        ((ListTableModel)myList.getModel()).setItems(myRevisions);
      }
      ((ListTableModel)myList.getModel()).fireTableDataChanged();
      updateDiff(0, 0);

    }
    else {
      ((ListTableModel)myList.getModel()).setItems(myRevisions);
      ((ListTableModel)myList.getModel()).fireTableDataChanged();
    }

  }

  private List<VcsFileRevision> filteredRevisions() throws FilesTooBigForDiffException, VcsException {
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

  private synchronized void updateDiff() {
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

  private synchronized void updateDiff(int first, int second) {
    if (myIsDisposed || myIsInLoading) return;
    List items = ((ListTableModel)myList.getModel()).getItems();
    VcsFileRevision firstRev = (VcsFileRevision)items.get(first);
    VcsFileRevision secondRev = (VcsFileRevision)items.get(second);

    if (VcsHistoryUtil.compare(firstRev, secondRev) > 0) {
      VcsFileRevision tmp = firstRev;
      firstRev = secondRev;
      secondRev = tmp;
    }

    if (myIsDisposed) return;
    try {
      myDiffPanel.setContents(new SimpleContent(getContentToShow(firstRev), myContentFileType),
                              new SimpleContent(getContentToShow(secondRev), myContentFileType));
    }
    catch (FilesTooBigForDiffException e) {
      myDiffPanel.setTooBigFileErrorContents();
    }
    catch (VcsException e) {
      final String text = canNoLoadMessage(e);
      myDiffPanel.setContents(new SimpleContent(text, myContentFileType),
                              new SimpleContent(text, myContentFileType));
      canNotLoadRevisionMessage(e);
    }
    myDiffPanel.setTitle1(VcsBundle.message("diff.content.title.revision.number", firstRev.getRevisionNumber()));
    myDiffPanel.setTitle2(VcsBundle.message("diff.content.title.revision.number", secondRev.getRevisionNumber()));

  }

  public synchronized void dispose() {
    myIsDisposed = true;
    super.dispose();
  }

  private JComponent createBottomPanel(final JComponent addComp) {
    Splitter splitter = new Splitter(true, getVcsConfiguration()
                                           .FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION);
    splitter.setDividerWidth(4);

    splitter.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (Splitter.PROP_PROPORTION.equals(evt.getPropertyName())) {
          getVcsConfiguration().FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION
          = ((Float)evt.getNewValue()).floatValue();
        }
      }
    });

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(createTablePanel(), BorderLayout.CENTER);
    tablePanel.add(myChangesOnlyCheckBox, BorderLayout.NORTH);

    splitter.setFirstComponent(tablePanel);
    splitter.setSecondComponent(createComments(addComp));

    return splitter;
  }

  private VcsConfiguration getVcsConfiguration() {
    return myActiveVcs.getConfiguration();
  }

  private JComponent createComments(final JComponent addComp) {
    final Splitter splitter = new Splitter(false);
    final JLabel label = new JLabel("Commit Message:") {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(getWidth(), 21);
      }
    };

    JPanel panel = new JPanel(new BorderLayout(4, 4));
    panel.add(label, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(myComments), BorderLayout.CENTER);

    myComments.setRows(5);
    myComments.setEditable(false);
    myComments.setLineWrap(true);

    splitter.setFirstComponent(panel);
    splitter.setSecondComponent(addComp);
    return splitter;
  }

  private JComponent createTablePanel() {
    return ScrollPaneFactory.createScrollPane(myList);
  }

  protected JComponent createCenterPanel() {
    return mySplitter;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  @NotNull
  protected Action[] createActions() {
    Action okAction = getOKAction();
    okAction.putValue(Action.NAME, VcsBundle.message("close.tab.action.name"));
    if (myHelpId != null) {
      return new Action[]{okAction, getHelpAction()};
    }
    else {
      return new Action[]{okAction};
    }
  }

  protected String getDimensionServiceKey() {
    return "VCS.FileHistoryDialog";
  }

  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    } else if (VcsDataKeys.VCS_VIRTUAL_FILE.is(dataId)) {
      return myFile;
    } else if (VcsDataKeys.VCS_FILE_REVISION.is(dataId)) {
      return myList.getSelectedObject();
    } else if (VcsDataKeys.VCS.is(dataId)) {
      return myActiveVcs.getKeyInstanceMethod();
    }
    return null;
  }

  protected String getContentToShow(VcsFileRevision revision) throws FilesTooBigForDiffException, VcsException {
    final Block block = getBlock(revision);
    if (block == null) return "";
    return block.getBlockContent();
  }

  @Nullable
  private Block getBlock(VcsFileRevision revision) throws FilesTooBigForDiffException, VcsException {
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

  private Block getBlock(int index) throws FilesTooBigForDiffException, VcsException {
    return index > 0 ? getBlock(myRevisions.get(index - 1)) : new Block(myEditor.getDocument().getText(), mySelectionStart, mySelectionEnd);
  }

}

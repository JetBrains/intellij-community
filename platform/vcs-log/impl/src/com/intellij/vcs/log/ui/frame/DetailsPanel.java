/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.printer.idea.PrintParameters;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.render.RefPainter;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
class DetailsPanel extends JPanel implements ListSelectionListener {

  private static final Logger LOG = Logger.getInstance("Vcs.Log");

  private static final String STANDARD_LAYER = "Standard";
  private static final String MESSAGE_LAYER = "Message";

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @NotNull private final RefsPanel myRefsPanel;
  @NotNull private final DataPanel myCommitDetailsPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JBLoadingPanel myLoadingPanel;

  @NotNull private VisiblePack myDataPack;

  @Nullable private VcsFullCommitDetails myCurrentCommitDetails;

  DetailsPanel(@NotNull VcsLogDataHolder logDataHolder,
               @NotNull VcsLogGraphTable graphTable,
               @NotNull VcsLogColorManager colorManager,
               @NotNull VisiblePack initialDataPack) {
    myLogDataHolder = logDataHolder;
    myGraphTable = graphTable;
    myDataPack = initialDataPack;

    myRefsPanel = new RefsPanel(colorManager);
    myCommitDetailsPanel = new DataPanel(logDataHolder.getProject());

    myScrollPane = new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    JPanel content = new JPanel(new MigLayout("flowy, ins 0, hidemode 3, gapy 0")) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = myScrollPane.getViewport().getWidth() - 5;
        return size;
      }
    };
    content.setOpaque(false);
    myScrollPane.setOpaque(false);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setViewportView(content);
    content.add(myRefsPanel, "");
    content.add(myCommitDetailsPanel, "");

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), logDataHolder, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      @Override
      public Color getBackground() {
        return getDetailsBackground();
      }
    };
    myLoadingPanel.add(myScrollPane);

    myMessagePanel = new MessagePanel();

    setLayout(new CardLayout());
    add(myLoadingPanel, STANDARD_LAYER);
    add(myMessagePanel, MESSAGE_LAYER);

    showMessage("No commits selected");
  }

  @Override
  public Color getBackground() {
    return getDetailsBackground();
  }

  private static Color getDetailsBackground() {
    return UIUtil.getTableBackground();
  }

  void updateDataPack(@NotNull VisiblePack dataPack) {
    myDataPack = dataPack;
  }

  @Override
  public void valueChanged(@Nullable ListSelectionEvent notUsed) {
    if (notUsed != null && notUsed.getValueIsAdjusting()) return;

    VcsFullCommitDetails newCommitDetails = null;

    int[] rows = myGraphTable.getSelectedRows();
    if (rows.length < 1) {
      showMessage("No commits selected");
    }
    else if (rows.length > 1) {
      showMessage("Several commits selected");
    }
    else {
      ((CardLayout)getLayout()).show(this, STANDARD_LAYER);
      int row = rows[0];
      GraphTableModel tableModel = (GraphTableModel)myGraphTable.getModel();
      VcsFullCommitDetails commitData = myLogDataHolder.getCommitDetailsGetter().getCommitData(row, tableModel);
      if (commitData == null) {
        showMessage("No commits selected");
        return;
      }
      if (commitData instanceof LoadingDetails) {
        myLoadingPanel.startLoading();
        myCommitDetailsPanel.setData(null);
        myRefsPanel.setRefs(Collections.<VcsRef>emptyList());
      }
      else {
        myLoadingPanel.stopLoading();
        myCommitDetailsPanel.setData(commitData);
        myRefsPanel.setRefs(sortRefs(commitData.getId(), commitData.getRoot()));
        newCommitDetails = commitData;
      }

      List<String> branches = null;
      if (!(commitData instanceof LoadingDetails)) {
        branches = myLogDataHolder.getContainingBranchesGetter().requestContainingBranches(commitData.getRoot(), commitData.getId());
      }
      myCommitDetailsPanel.setBranches(branches);

      if (!Comparing.equal(myCurrentCommitDetails, newCommitDetails)) {
        myCurrentCommitDetails = newCommitDetails;
        myScrollPane.getVerticalScrollBar().setValue(0);
      }
    }
  }

  private void showMessage(String text) {
    myLoadingPanel.stopLoading();
    ((CardLayout)getLayout()).show(this, MESSAGE_LAYER);
    myMessagePanel.setText(text);
  }

  @NotNull
  private List<VcsRef> sortRefs(@NotNull Hash hash, @NotNull VirtualFile root) {
    Collection<VcsRef> refs = myDataPack.getRefsModel().refsToCommit(hash);
    return ContainerUtil.sorted(refs, myLogDataHolder.getLogProvider(root).getReferenceManager().getComparator());
  }

  private static class DataPanel extends JEditorPane {

    @NotNull private final Project myProject;
    @Nullable private String myBranchesText = null;
    private String myMainText;

    DataPanel(@NotNull Project project) {
      super(UIUtil.HTML_MIME, "");
      setEditable(false);
      myProject = project;
      addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
      setOpaque(false);
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

      final DefaultCaret caret = (DefaultCaret) getCaret();
      caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    }

    void setData(@Nullable VcsFullCommitDetails commit) {
      if (commit == null) {
        myMainText = null;
      }
      else {
        String body = getMessageText(commit);
        String header = commit.getId().toShortString() + " " + getAuthorText(commit);
        myMainText = header + "<br/>" + body;
      }
      update();
    }

    void setBranches(@Nullable List<String> branches) {
      if (branches == null) {
        myBranchesText = null;
      }
      else {
        myBranchesText = StringUtil.join(branches, ", ");
      }
      update();
    }

    private void update() {
      if (myMainText == null) {
        setText("");
      }
      else {
        setText("<html><head>" +
                UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) +
                "</head><body>" +
                myMainText +
                "<br/>" +
                "<br/>" +
                "<i>Contained in branches:</i> " +
                (myBranchesText == null ? "<i>loading...</i>" : myBranchesText) +
                "</body></html>");
      }
      revalidate();
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = Math.max(size.height, 4 * getFontMetrics(getFont()).getHeight());
      return size;
    }

    private String getMessageText(VcsFullCommitDetails commit) {
      String fullMessage = commit.getFullMessage();
      int separator = fullMessage.indexOf("\n\n");
      String subject = separator > 0 ? fullMessage.substring(0, separator) : fullMessage;
      String description = fullMessage.substring(subject.length());
      return "<b>" + IssueLinkHtmlRenderer.formatTextWithLinks(myProject, subject) + "</b>" +
             IssueLinkHtmlRenderer.formatTextWithLinks(myProject, description);
    }

    private static String getAuthorText(VcsFullCommitDetails commit) {
      String authorText = commit.getAuthor().getName() + " at " + DateFormatUtil.formatDateTime(commit.getAuthorTime());
      if (!commit.getAuthor().equals(commit.getCommitter())) {
        String commitTime;
        if (commit.getAuthorTime() != commit.getCommitTime()) {
          commitTime = " at " + DateFormatUtil.formatDateTime(commit.getCommitTime());
        }
        else {
          commitTime = "";
        }
        authorText += " (committed by " + commit.getCommitter().getName() + commitTime + ")";
      }
      else if (commit.getAuthorTime() != commit.getCommitTime()) {
        authorText += " (committed at " + DateFormatUtil.formatDateTime(commit.getCommitTime()) + ")";
      }
      return authorText;
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }
  }

  private static class RefsPanel extends JPanel {

    @NotNull private final RefPainter myRefPainter;
    @NotNull private List<VcsRef> myRefs;

    RefsPanel(@NotNull VcsLogColorManager colorManager) {
      super(new FlowLayout(FlowLayout.LEADING, 0, 2));
      myRefPainter = new RefPainter(colorManager, false);
      myRefs = Collections.emptyList();
      setOpaque(false);
    }

    void setRefs(@NotNull List<VcsRef> refs) {
      removeAll();
      myRefs = refs;
      for (VcsRef ref : refs) {
        add(new SingleRefPanel(myRefPainter, ref));
      }
      setVisible(!myRefs.isEmpty());
      revalidate();
      repaint();
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }
  }

  private static class SingleRefPanel extends JPanel {
    @NotNull private final RefPainter myRefPainter;
    @NotNull private VcsRef myRef;

    SingleRefPanel(@NotNull RefPainter refPainter, @NotNull VcsRef ref) {
      myRefPainter = refPainter;
      myRef = ref;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      myRefPainter.draw((Graphics2D)g, Collections.singleton(myRef), 0, getWidth());
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }

    @Override
    public Dimension getPreferredSize() {
      int width = myRefPainter.getComponentWidth(myRef.getName(), getFontMetrics(RefPainter.DEFAULT_FONT));
      return new Dimension(width, PrintParameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP);
    }
  }

  private static class MessagePanel extends NonOpaquePanel {

    private final JLabel myLabel;

    MessagePanel() {
      super(new BorderLayout());
      myLabel = new JLabel();
      myLabel.setForeground(UIUtil.getInactiveTextColor());
      myLabel.setHorizontalAlignment(SwingConstants.CENTER);
      myLabel.setVerticalAlignment(SwingConstants.CENTER);
      add(myLabel);
    }

    void setText(String text) {
      myLabel.setText(text);
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }
  }
}

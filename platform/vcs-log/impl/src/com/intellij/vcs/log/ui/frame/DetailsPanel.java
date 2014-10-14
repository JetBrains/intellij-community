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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.AsyncProcessIcon;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
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
  @NotNull private final DataPanel myHashAuthorPanel;
  @NotNull private final DataPanel myMessageDataPanel;
  @NotNull private final ContainingBranchesPanel myContainingBranchesPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final JBLoadingPanel myLoadingPanel;

  @NotNull private VisiblePack myDataPack;

  DetailsPanel(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogGraphTable graphTable, @NotNull VcsLogColorManager colorManager,
               @NotNull VisiblePack initialDataPack) {
    myLogDataHolder = logDataHolder;
    myGraphTable = graphTable;
    myDataPack = initialDataPack;

    myRefsPanel = new RefsPanel(colorManager);
    myHashAuthorPanel = new DataPanel(logDataHolder.getProject(), false);

    final JScrollPane scrollPane = new JBScrollPane() {
      @Override
      public Border getBorder() {
        return getVerticalScrollBar().isVisible() ? super.getBorder() : null;
      }
    };
    myMessageDataPanel = new DataPanel(logDataHolder.getProject(), true) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = scrollPane.getViewport().getWidth() - 5;
        return size;
      }
    };
    scrollPane.setViewportView(myMessageDataPanel);

    myContainingBranchesPanel = new ContainingBranchesPanel();
    myMessagePanel = new MessagePanel();

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), logDataHolder, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    JPanel header = new NonOpaquePanel(new BorderLayout());
    header.add(myRefsPanel, BorderLayout.NORTH);
    header.add(myHashAuthorPanel, BorderLayout.SOUTH);
    myLoadingPanel.add(header, BorderLayout.NORTH);
    myLoadingPanel.add(scrollPane, BorderLayout.CENTER);
    myLoadingPanel.add(myContainingBranchesPanel, BorderLayout.SOUTH);
    myLoadingPanel.setOpaque(false);

    setLayout(new CardLayout());
    add(myLoadingPanel, STANDARD_LAYER);
    add(myMessagePanel, MESSAGE_LAYER);

    setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
    showMessage("No commits selected");
  }

  @Override
  public Color getBackground() {
    return UIUtil.getTableBackground();
  }

  void updateDataPack(@NotNull VisiblePack dataPack) {
    myDataPack = dataPack;
  }

  @Override
  public void valueChanged(@Nullable ListSelectionEvent notUsed) {
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
      Hash hash = tableModel.getHashAtRow(row);
      VcsFullCommitDetails commitData = myLogDataHolder.getCommitDetailsGetter().getCommitData(row, tableModel);
      if (commitData == null || hash == null) {
        showMessage("No commits selected");
        return;
      }
      if (commitData instanceof LoadingDetails) {
        myLoadingPanel.startLoading();
        myHashAuthorPanel.setData(null);
        myMessageDataPanel.setData(null);
        myRefsPanel.setRefs(Collections.<VcsRef>emptyList());
      }
      else {
        myLoadingPanel.stopLoading();
        myHashAuthorPanel.setData(commitData);
        myMessageDataPanel.setData(commitData);
        myRefsPanel.setRefs(sortRefs(hash, commitData.getRoot()));
      }

      List<String> branches = null;
      if (!(commitData instanceof LoadingDetails)) {
        branches = myLogDataHolder.getContainingBranchesGetter().requestContainingBranches(commitData.getRoot(), hash);
      }
      myContainingBranchesPanel.setBranches(branches);
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
    private final boolean myMessageMode;

    DataPanel(@NotNull Project project, boolean messageMode) {
      super(UIUtil.HTML_MIME, "");
      myMessageMode = messageMode;
      setEditable(false);
      myProject = project;
      addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
      setOpaque(false);
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    }

    void setData(@Nullable VcsFullCommitDetails commit) {
      if (commit == null) {
        setText("");
      }
      else {
        String body;
        if (myMessageMode) {
          body = getMessageText(commit);
        } else {
          body = commit.getId().asString() + "<br/>" + getAuthorText(commit);
        }
        setText("<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" + body + "</body></html>");
        setCaretPosition(0);
      }
      revalidate();
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      int h = getFontMetrics(getFont()).getHeight();
      size.height = Math.max(size.height, myMessageMode ? 5 * h : 2 * h);
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
        if (commit.getAuthorTime() != commit.getTimestamp()) {
          commitTime = " at " + DateFormatUtil.formatDateTime(commit.getTimestamp());
        }
        else {
          commitTime = "";
        }
        authorText += " (committed by " + commit.getCommitter().getName() + commitTime + ")";
      }
      else if (commit.getAuthorTime() != commit.getTimestamp()) {
        authorText += " (committed at " + DateFormatUtil.formatDateTime(commit.getTimestamp()) + ")";
      }
      return authorText;
    }
  }

  private static class ContainingBranchesPanel extends JPanel {

    private final JComponent myLoadingComponent;
    private final JTextField myBranchesList;

    ContainingBranchesPanel() {
      JLabel label = new JBLabel("Contained in branches: ") {
        @Override
        public Font getFont() {
          return UIUtil.getLabelFont().deriveFont(Font.ITALIC);
        }
      };
      myLoadingComponent = new NonOpaquePanel(new BorderLayout());
      myLoadingComponent.add(new AsyncProcessIcon("Loading..."), BorderLayout.WEST);
      myLoadingComponent.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
      myBranchesList = new JBTextField("") {
        private final Border gtkBorder = new LineBorder(UIUtil.getTextFieldBackground(), 3) {
          @Override
          public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            lineColor = UIUtil.getTextFieldBackground();
            super.paintBorder(c, g, x, y, width, height);
          }
        };
        private final Border emptyBorder = IdeBorderFactory.createEmptyBorder();

        @Override
        public Border getBorder() {
          // setting border to empty does not help with gtk l&f
          // so I just cover it completely with my line border
          return UIUtil.isUnderGTKLookAndFeel() ? gtkBorder : emptyBorder;
        }
      };
      myBranchesList.setOpaque(false);
      myBranchesList.setEditable(false);
      setOpaque(false);
      setLayout(new BorderLayout());
      add(label, BorderLayout.WEST);
      add(Box.createHorizontalGlue(), BorderLayout.EAST);
    }

    void setBranches(@Nullable List<String> branches) {
      if (branches == null) {
        remove(myBranchesList);
        add(myLoadingComponent, BorderLayout.CENTER);
      }
      else {
        remove(myLoadingComponent);
        myBranchesList.setText(StringUtil.join(branches, ", "));
        add(myBranchesList, BorderLayout.CENTER);
      }
      revalidate();
      repaint();
    }
  }

  private static class RefsPanel extends JPanel {

    @NotNull private final RefPainter myRefPainter;
    @NotNull private List<VcsRef> myRefs;

    RefsPanel(@NotNull VcsLogColorManager colorManager) {
      myRefPainter = new RefPainter(colorManager, false);
      myRefs = Collections.emptyList();
      setPreferredSize(new Dimension(-1, PrintParameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP));
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      // TODO when the right margin reaches, draw on the second line
      myRefPainter.draw((Graphics2D)g, myRefs, 0, getWidth());
    }

    void setRefs(@NotNull List<VcsRef> refs) {
      myRefs = refs;
      setVisible(!myRefs.isEmpty());
      repaint();
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
  }
}

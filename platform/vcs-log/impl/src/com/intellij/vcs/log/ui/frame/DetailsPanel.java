package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.Function;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.PrintParameters;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.render.RefPainter;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  @NotNull private final DataPanel myDataPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final JBLoadingPanel myLoadingPanel;

  DetailsPanel(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogGraphTable graphTable, @NotNull VcsLogColorManager colorManager) {
    super(new CardLayout());
    myLogDataHolder = logDataHolder;
    myGraphTable = graphTable;

    myRefsPanel = new RefsPanel(colorManager);
    myDataPanel = new DataPanel(logDataHolder.getProject());
    myMessagePanel = new MessagePanel();

    Box content = Box.createVerticalBox();
    content.add(myRefsPanel);
    content.add(myDataPanel);
    content.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), logDataHolder, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    myLoadingPanel.add(ScrollPaneFactory.createScrollPane(content));

    add(myLoadingPanel, STANDARD_LAYER);
    add(myMessagePanel, MESSAGE_LAYER);

    setBackground(UIUtil.getTableBackground());
    showMessage("No commits selected");
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
      Hash hash = ((AbstractVcsLogTableModel)myGraphTable.getModel()).getHashAtRow(row);
      if (hash == null) {
        showMessage("No commits selected");
        return;
      }

      VcsFullCommitDetails commitData = myLogDataHolder.getCommitDetailsGetter().getCommitData(hash);
      DataPack dataPack = myLogDataHolder.getDataPack();
      List<VcsRef> branches = myLogDataHolder.getContainingBranchesGetter().requestContainingBranches(dataPack, dataPack.getNode(row));
      if (commitData instanceof LoadingDetails || branches == null) {
        myLoadingPanel.startLoading();
        myDataPanel.setData(null, null);
        myRefsPanel.setRefs(Collections.<VcsRef>emptyList());
      }
      else {
        myLoadingPanel.stopLoading();
        myDataPanel.setData(commitData, branches);
        myRefsPanel.setRefs(sortRefs(hash, commitData.getRoot()));
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
    Collection<VcsRef> refs = myLogDataHolder.getDataPack().getRefsModel().refsToCommit(hash);
    return myLogDataHolder.getLogProvider(root).getReferenceManager().sort(refs);
  }

  private static class DataPanel extends JEditorPane {

    @NotNull private final Project myProject;

    DataPanel(@NotNull Project project) {
      super(UIUtil.HTML_MIME, "");
      setEditable(false);
      myProject = project;
      addHyperlinkListener(new BrowserHyperlinkListener());
      setPreferredSize(new Dimension(150, 100));
    }

    void setData(@Nullable VcsFullCommitDetails commit, @Nullable List<VcsRef> branches) {
      if (commit == null || branches == null) {
        setText("");
      }
      else {
        String body = getHashText(commit) + "<br/>" + getAuthorText(commit) + "<p>" + getMessageText(commit) + "</p>" +
                      "<p>" + getContainedBranchesText(branches) + "</p>";
        setText("<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" + body + "</body></html>");
        setCaretPosition(0);
      }
    }

    private static String getContainedBranchesText(List<VcsRef> branches) {
      return "<i>Contained in branches:</i> " + StringUtil.join(branches, new Function<VcsRef, String>() {
        @Override
        public String fun(VcsRef ref) {
          return ref.getName();
        }
      }, ", ");
    }

    private String getMessageText(VcsFullCommitDetails commit) {
      String subject = commit.getSubject();
      String description = subject.length() < commit.getFullMessage().length() ? commit.getFullMessage().substring(subject.length()) : "";
      return "<b>" + IssueLinkHtmlRenderer.formatTextWithLinks(myProject, subject) + "</b>" +
             IssueLinkHtmlRenderer.formatTextWithLinks(myProject, description);
    }

    private static String getHashText(VcsFullCommitDetails commit) {
      return commit.getHash().asString();
    }

    private static String getAuthorText(VcsFullCommitDetails commit) {
      String authorText = commit.getAuthor().getName() + " at " + DateFormatUtil.formatDateTime(commit.getAuthorTime());
      if (!commit.getAuthor().equals(commit.getCommitter())) {
        String commitTime;
        if (commit.getAuthorTime() != commit.getTime()) {
          commitTime = " at " + DateFormatUtil.formatDateTime(commit.getTime());
        }
        else {
          commitTime = "";
        }
        authorText += " (committed by " + commit.getCommitter().getName() + commitTime + ")";
      }
      else if (commit.getAuthorTime() != commit.getTime()) {
        authorText += " (committed at " + DateFormatUtil.formatDateTime(commit.getTime()) + ")";
      }
      return authorText;
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

  private static class MessagePanel extends JPanel {

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

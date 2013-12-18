package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.PrintParameters;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.render.RefPainter;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import net.miginfocom.swing.MigLayout;
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
  @NotNull private final ContainingBranchesPanel myContainingBranchesPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final JBLoadingPanel myLoadingPanel;

  DetailsPanel(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogGraphTable graphTable, @NotNull VcsLogColorManager colorManager) {
    super(new CardLayout());
    myLogDataHolder = logDataHolder;
    myGraphTable = graphTable;

    myRefsPanel = new RefsPanel(colorManager);
    myDataPanel = new DataPanel(logDataHolder.getProject());
    myContainingBranchesPanel = new ContainingBranchesPanel();
    myMessagePanel = new MessagePanel();

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane();
    JPanel content = new JPanel(new MigLayout("flowy, ins 0, fill, hidemode 3")) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = scrollPane.getViewport().getWidth() - 5;
        return size;
      }
    };
    content.setOpaque(false);
    scrollPane.setViewportView(content);
    content.add(myRefsPanel, "shrinky, pushx, growx");
    content.add(myDataPanel, "growy, push");
    content.add(myContainingBranchesPanel, "shrinky, pushx, growx");
    content.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), logDataHolder, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    myLoadingPanel.add(scrollPane);

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
      AbstractVcsLogTableModel tableModel = (AbstractVcsLogTableModel)myGraphTable.getModel();
      Hash hash = tableModel.getHashAtRow(row);
      if (hash == null) {
        showMessage("No commits selected");
        return;
      }

      VcsFullCommitDetails commitData = myLogDataHolder.getCommitDetailsGetter().getCommitData(hash, tableModel.getAroundProvider());
      if (commitData instanceof LoadingDetails) {
        myLoadingPanel.startLoading();
        myDataPanel.setData(null);
        myRefsPanel.setRefs(Collections.<VcsRef>emptyList());
      }
      else {
        myLoadingPanel.stopLoading();
        myDataPanel.setData(commitData);
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
      setOpaque(false);
    }

    void setData(@Nullable VcsFullCommitDetails commit) {
      if (commit == null) {
        setText("");
      }
      else {
        String body = getHashText(commit) + "<br/>" + getAuthorText(commit) + "<p>" + getMessageText(commit) + "</p>";
        setText("<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" + body + "</body></html>");
        setCaretPosition(0);
      }
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

  private static class ContainingBranchesPanel extends JPanel {

    private final JComponent myLoadingIcon;
    private final JTextField myBranchesList;

    ContainingBranchesPanel() {
      JLabel label = new JBLabel("Contained in branches: ") {
        @Override
        public Font getFont() {
          return UIUtil.getLabelFont().deriveFont(Font.ITALIC);
        }
      };
      myLoadingIcon = new AsyncProcessIcon("Loading...");
      myBranchesList = new JBTextField("");
      myBranchesList.setEditable(false);
      myBranchesList.setBorder(null);

      setOpaque(false);
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      add(label);
      add(myLoadingIcon);
      add(myBranchesList);
      add(Box.createHorizontalGlue());
    }

    void setBranches(@Nullable List<String> branches) {
      if (branches == null) {
        myLoadingIcon.setVisible(true);
        myBranchesList.setVisible(false);
      }
      else {
        myLoadingIcon.setVisible(false);
        myBranchesList.setVisible(true);
        myBranchesList.setText(getContainedBranchesText(branches));
      }
    }

    @NotNull
    private static String getContainedBranchesText(@NotNull List<String> branches) {
      return StringUtil.join(branches, ", ");
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

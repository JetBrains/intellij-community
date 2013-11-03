package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.GridBag;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
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
    myDataPanel = new DataPanel();
    myMessagePanel = new MessagePanel();

    Box box = Box.createVerticalBox();
    box.add(myRefsPanel);
    box.add(myDataPanel);

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), logDataHolder);
    myLoadingPanel.add(ScrollPaneFactory.createScrollPane(box));

    add(myLoadingPanel, STANDARD_LAYER);
    add(myMessagePanel, MESSAGE_LAYER);

    setBackground(UIUtil.getTableBackground());
  }

  @Override
  public void valueChanged(@Nullable ListSelectionEvent notUsed) {
    int[] rows = myGraphTable.getSelectedRows();
    if (rows.length < 1) {
      showMessage("Nothing selected");
    }
    else if (rows.length > 1) {
      showMessage("Several commits selected");
    }
    else {
      ((CardLayout)getLayout()).show(this, STANDARD_LAYER);
      Hash hash = ((AbstractVcsLogTableModel)myGraphTable.getModel()).getHashAtRow(rows[0]);
      if (hash == null) {
        showMessage("No commits selected");
        return;
      }

      VcsFullCommitDetails commitData = myLogDataHolder.getCommitDetailsGetter().getCommitData(hash);
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

  private static class DataPanel extends JPanel {

    @NotNull private final JLabel myHashLabel;
    @NotNull private final JTextField myAuthor;
    @NotNull private final JTextArea myCommitMessage;

    DataPanel() {
      super();
      myHashLabel = new LinkLabel("", null, new LinkListener() {
        @Override
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          CopyPasteManager.getInstance().setContents(new StringSelection(myHashLabel.getText()));
        }
      });

      myAuthor = new JBTextField();
      myAuthor.setEditable(false);
      myAuthor.setBorder(null);

      myCommitMessage = new JTextArea();
      myCommitMessage.setEditable(false);
      myCommitMessage.setBorder(null);

      setLayout(new GridBagLayout());
      GridBag g = new GridBag()
        .setDefaultAnchor(GridBagConstraints.NORTHWEST)
        .setDefaultFill(GridBagConstraints.HORIZONTAL)
        .setDefaultWeightX(1.0);
      add(myHashLabel, g.nextLine().next());
      add(myAuthor, g.nextLine().next());
      add(myCommitMessage, g.nextLine().next());
      add(Box.createVerticalGlue(), g.nextLine().next().weighty(1.0).fillCell());

      setOpaque(false);
    }

    void setData(@Nullable VcsFullCommitDetails commit) {
      if (commit == null) {
        myHashLabel.setText("");
        myCommitMessage.setText("");
        myAuthor.setText("");
      }
      else {
        myHashLabel.setText(commit.getHash().toShortString());
        myCommitMessage.setText(commit.getFullMessage());
        myCommitMessage.setCaretPosition(0);

        String authorText = commit.getAuthorName() + " at " + DateFormatUtil.formatDateTime(commit.getAuthorTime());
        if (!commit.getAuthorName().equals(commit.getCommitterName()) || !commit.getAuthorEmail().equals(commit.getCommitterEmail())) {
          authorText += " (committed by " + commit.getCommitterName() +
                        " at " + DateFormatUtil.formatDateTime(commit.getCommitTime()) + ")";
        }
        myAuthor.setText(authorText);
      }
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

  private static class MessagePanel extends JPanel {

    private final JLabel myLabel;

    MessagePanel() {
      super(new BorderLayout());
      myLabel = new JLabel();
      myLabel.setForeground(UIUtil.getInactiveTextColor());
      add(myLabel);
    }

    void setText(String text) {
      myLabel.setText(text);
    }
  }
}

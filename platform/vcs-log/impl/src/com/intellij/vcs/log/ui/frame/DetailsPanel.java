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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsAdapter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.NotNullProducer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.render.VcsRefPainter;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Position;
import java.awt.*;
import java.io.IOException;
import java.io.StringWriter;
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

  @NotNull private final ReferencesPanel myReferencesPanel;
  @NotNull private final DataPanel myCommitDetailsPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JPanel myMainContentPanel;

  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private VisiblePack myDataPack;
  @Nullable private VcsFullCommitDetails myCurrentCommitDetails;

  DetailsPanel(@NotNull VcsLogDataHolder logDataHolder,
               @NotNull VcsLogGraphTable graphTable,
               @NotNull VcsLogColorManager colorManager,
               @NotNull VisiblePack initialDataPack) {
    myLogDataHolder = logDataHolder;
    myGraphTable = graphTable;
    myColorManager = colorManager;
    myDataPack = initialDataPack;

    myReferencesPanel = new ReferencesPanel(myColorManager);
    myCommitDetailsPanel = new DataPanel(logDataHolder.getProject(), logDataHolder.isMultiRoot(), logDataHolder);

    myScrollPane = new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(8);
    myMainContentPanel = new JPanel(new MigLayout("flowy, ins 0, hidemode 3, gapy 0")) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (myCommitDetailsPanel.isExpanded()) {
          return size;
        }
        size.width = myScrollPane.getViewport().getWidth() - 5;
        return size;
      }
    };
    myMainContentPanel.setOpaque(false);
    myScrollPane.setOpaque(false);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setViewportView(myMainContentPanel);
    myScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    myScrollPane.setViewportBorder(IdeBorderFactory.createEmptyBorder());
    myMainContentPanel.add(myReferencesPanel, "");
    myMainContentPanel.add(myCommitDetailsPanel, "");

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

  @NotNull
  public static String formatDateTime(long time) {
    return " on " + DateFormatUtil.formatDate(time) + " at " + DateFormatUtil.formatTime(time);
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
      if (commitData instanceof LoadingDetails) {
        myLoadingPanel.startLoading();
        myCommitDetailsPanel.setData(null);
        myReferencesPanel.setReferences(Collections.<VcsRef>emptyList());
        updateDetailsBorder(null);
      }
      else {
        myLoadingPanel.stopLoading();
        myCommitDetailsPanel.setData(commitData);
        myReferencesPanel.setReferences(sortRefs(commitData.getId(), commitData.getRoot()));
        updateDetailsBorder(commitData);
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

  private void updateDetailsBorder(@Nullable VcsFullCommitDetails data) {
    if (data == null || !myColorManager.isMultipleRoots()) {
      myMainContentPanel.setBorder(BorderFactory.createEmptyBorder(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2,
                                                                   VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2, 0, 0));
    }
    else {
      Color color = VcsLogGraphTable.getRootBackgroundColor(data.getRoot(), myColorManager);
      myMainContentPanel.setBorder(new CompoundBorder(new MatteBorder(0, VcsLogGraphTable.ROOT_INDICATOR_COLORED_WIDTH, 0, 0, color),
                                                      new MatteBorder(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2,
                                                                      VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH, 0, 0,
                                                                      new JBColor(new NotNullProducer<Color>() {
                                                                        @NotNull
                                                                        @Override
                                                                        public Color produce() {
                                                                          return getDetailsBackground();
                                                                        }
                                                                      }))));
    }
  }

  private void showMessage(String text) {
    myLoadingPanel.stopLoading();
    ((CardLayout)getLayout()).show(this, MESSAGE_LAYER);
    myMessagePanel.setText(text);
  }

  @NotNull
  private List<VcsRef> sortRefs(@NotNull Hash hash, @NotNull VirtualFile root) {
    Collection<VcsRef> refs = myDataPack.getRefsModel().refsToCommit(hash, root);
    return ContainerUtil.sorted(refs, myLogDataHolder.getLogProvider(root).getReferenceManager().getLabelsOrderComparator());
  }

  private static class DataPanel extends JEditorPane {
    public static final int BRANCHES_LIMIT = 6;
    public static final int BRANCHES_TABLE_COLUMN_COUNT = 3;
    @NotNull public static final String LEFT_ALIGN = "left";
    @NotNull private static String SHOW_OR_HIDE_BRANCHES = "Show or Hide Branches";

    @NotNull private final Project myProject;
    private final boolean myMultiRoot;
    private String myMainText;
    @Nullable private List<String> myBranches;
    private boolean myExpanded = false;

    DataPanel(@NotNull Project project, boolean multiRoot, @NotNull Disposable disposable) {
      super(UIUtil.HTML_MIME, "");
      myProject = project;
      myMultiRoot = multiRoot;
      setEditable(false);
      setOpaque(false);
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

      EditorColorsManager.getInstance().addEditorColorsListener(new EditorColorsAdapter() {
        @Override
        public void globalSchemeChange(EditorColorsScheme scheme) {
          update();
        }
      }, disposable);

      DefaultCaret caret = (DefaultCaret)getCaret();
      caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

      addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && SHOW_OR_HIDE_BRANCHES.equals(e.getDescription())) {
            myExpanded = !myExpanded;
            update();
          }
          else {
            BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e);
          }
        }
      });
    }

    void setData(@Nullable VcsFullCommitDetails commit) {
      if (commit == null) {
        myMainText = null;
      }
      else {
        String header = commit.getId().toShortString() + " " + getAuthorText(commit) +
                        (myMultiRoot ? " [" + commit.getRoot().getName() + "]" : "");
        String body = getMessageText(commit);
        myMainText = header + "<br/>" + body;
      }
      update();
    }

    void setBranches(@Nullable List<String> branches) {
      if (branches == null) {
        myBranches = null;
      }
      else {
        myBranches = branches;
      }
      myExpanded = false;
      update();
    }

    private void update() {
      if (myMainText == null) {
        setText("");
      }
      else {
        setText("<html><head>" +
                UIUtil.getCssFontDeclaration(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN)) +
                "</head><body>" +
                myMainText +
                "<br/>" +
                "<br/>" +
                getBranchesText() +
                "</body></html>");
      }
      revalidate();
      repaint();
    }

    @NotNull
    private String getBranchesText() {
      if (myBranches == null) {
        return "<i>In branches: loading...</i>";
      }
      if (myBranches.isEmpty()) return "<i>Not in any branch</i>";
      if (myExpanded) {
        int rowCount = (int)Math.ceil((double)myBranches.size() / BRANCHES_TABLE_COLUMN_COUNT);

        int[] means = new int[BRANCHES_TABLE_COLUMN_COUNT - 1];
        int[] max = new int[BRANCHES_TABLE_COLUMN_COUNT - 1];

        for (int i = 0; i < rowCount; i++){
          for (int j = 0; j < BRANCHES_TABLE_COLUMN_COUNT - 1; j++) {
            int index = rowCount * j + i;
            if (index < myBranches.size()) {
              means[j] += myBranches.get(index).length();
              max[j] = Math.max(myBranches.get(index).length(), max[j]);
            }
          }
        }
        for (int j = 0; j < BRANCHES_TABLE_COLUMN_COUNT - 1; j++) {
          means[j] /= rowCount;
        }

        HtmlTableBuilder builder = new HtmlTableBuilder();
        for (int i = 0; i < rowCount; i++) {
          builder.startRow();
          if (i == 0) {
            builder.append("<i>In " + myBranches.size() + " branches, </i><a href=\"" + SHOW_OR_HIDE_BRANCHES + "\"><i>hide</i></a>: ");
          }
          else {
            builder.append("");
          }

          for (int j = 0; j < BRANCHES_TABLE_COLUMN_COUNT; j++) {
            int index = rowCount * j + i;
            if (index >= myBranches.size()) {
              builder.append("");
            }
            else {
              String branch = myBranches.get(index);
              if (index != myBranches.size() - 1) {
                int space = 0;
                if (j < BRANCHES_TABLE_COLUMN_COUNT - 1 && branch.length() == max[j]) {
                  space = Math.max(means[j] + 20 - max[j], 5);
                }
                builder.append(branch + "," + StringUtil.repeat("&nbsp;", space), LEFT_ALIGN);
              }
              else {
                builder.append(branch, LEFT_ALIGN);
              }
            }
          }

          builder.endRow();
        }

        return builder.build();
      }
      else {
        String branchText;
        if (myBranches.size() <= BRANCHES_LIMIT) {
          branchText = StringUtil.join(myBranches, ", ");
        }
        else {
          branchText = StringUtil.join(ContainerUtil.getFirstItems(myBranches, BRANCHES_LIMIT), ", ") +
                       ", ... <a href=\"" +
                       SHOW_OR_HIDE_BRANCHES +
                       "\"><i>Show All</i></a>";
        }
        return "<i>In " + myBranches.size() + StringUtil.pluralize(" branch", myBranches.size()) + ":</i> " + branchText;
      }
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
      return "<b>" + escapeMultipleSpaces(IssueLinkHtmlRenderer.formatTextWithLinks(myProject, subject)) + "</b>" +
             escapeMultipleSpaces(IssueLinkHtmlRenderer.formatTextWithLinks(myProject, description));
    }

    private String escapeMultipleSpaces(String text) {
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == ' ') {
          if (i == text.length() - 1 || text.charAt(i + 1) != ' ') {
            result.append(' ');
          }
          else {
            result.append("&nbsp;");
          }
        }
        else {
          result.append(text.charAt(i));
        }
      }
      return result.toString();
    }

    private static String getAuthorText(VcsFullCommitDetails commit) {
      long authorTime = commit.getAuthorTime();
      long commitTime = commit.getCommitTime();

      String authorText = commit.getAuthor().getName() + formatDateTime(authorTime);
      if (!commit.getAuthor().equals(commit.getCommitter())) {
        String commitTimeText;
        if (authorTime != commitTime) {
          commitTimeText = formatDateTime(commitTime);
        }
        else {
          commitTimeText = "";
        }
        authorText += " (committed by " + commit.getCommitter().getName() + commitTimeText + ")";
      }
      else if (authorTime != commitTime) {
        authorText += " (committed " + formatDateTime(commitTime) + ")";
      }
      return authorText;
    }

    @Override
    public String getSelectedText() {
      Document doc = getDocument();
      int start = getSelectionStart();
      int end = getSelectionEnd();

      try {
        Position p0 = doc.createPosition(start);
        Position p1 = doc.createPosition(end);
        StringWriter sw = new StringWriter(p1.getOffset() - p0.getOffset());
        getEditorKit().write(sw, doc, p0.getOffset(), p1.getOffset() - p0.getOffset());

        return StringUtil.removeHtmlTags(sw.toString());
      }
      catch (BadLocationException e) {
        LOG.warn(e);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      return super.getSelectedText();
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }

    public boolean isExpanded() {
      return myExpanded;
    }
  }

  private static class ReferencesPanel extends JPanel {
    @NotNull private final VcsRefPainter myReferencePainter;
    @NotNull private List<VcsRef> myReferences;

    ReferencesPanel(@NotNull VcsLogColorManager colorManager) {
      super(new FlowLayout(FlowLayout.LEADING, 4, 2));
      myReferencePainter = new VcsRefPainter(colorManager, false);
      myReferences = Collections.emptyList();
      setOpaque(false);
    }

    void setReferences(@NotNull List<VcsRef> references) {
      removeAll();
      myReferences = references;
      for (VcsRef reference : references) {
        add(new SingleReferencePanel(myReferencePainter, reference));
      }
      setVisible(!myReferences.isEmpty());
      revalidate();
      repaint();
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }
  }

  private static class SingleReferencePanel extends JPanel {
    @NotNull private final VcsRefPainter myRefPainter;
    @NotNull private VcsRef myReference;

    SingleReferencePanel(@NotNull VcsRefPainter referencePainter, @NotNull VcsRef reference) {
      myRefPainter = referencePainter;
      myReference = reference;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      myRefPainter.paint(myReference, g, 0, 0);
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }

    @Override
    public Dimension getPreferredSize() {
      return myRefPainter.getSize(myReference, this);
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

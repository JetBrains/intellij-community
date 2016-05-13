/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.render.VcsRefPainter;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
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
import java.util.Set;

/**
 * @author Kirill Likhodedov
 */
class DetailsPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("Vcs.Log");

  private static final String LINK_HREF = "show-hide-branches";
  private static final int MAX_ROWS = 50;

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JPanel myMainContentPanel;

  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private VisiblePack myDataPack;
  @NotNull private Set<VcsFullCommitDetails> myCurrentCommitDetails = Collections.emptySet();

  private final StatusText myEmptyText;

  DetailsPanel(@NotNull VcsLogData logData,
               @NotNull VcsLogGraphTable graphTable,
               @NotNull VcsLogColorManager colorManager,
               @NotNull VisiblePack initialDataPack,
               @NotNull Disposable parent) {
    myLogData = logData;
    myGraphTable = graphTable;
    myColorManager = colorManager;
    myDataPack = initialDataPack;

    myScrollPane = new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(JBUI.scale(10));
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(JBUI.scale(10));
    myMainContentPanel = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        boolean expanded = false;
        for (Component c : getComponents()) {
          if (c instanceof CommitPanel && ((CommitPanel)c).isExpanded()) {
            expanded = true;
            break;
          }
        }
        if (expanded) {
          return size;
        }
        size.width = myScrollPane.getViewport().getWidth() - 5;
        return size;
      }

      @Override
      public Color getBackground() {
        return getDetailsBackground();
      }

      @Override
      protected void paintChildren(Graphics g) {
        if (StringUtil.isNotEmpty(myEmptyText.getText())) {
          myEmptyText.paint(this, g);
        }
        else {
          super.paintChildren(g);
        }
      }
    };
    myMainContentPanel.setLayout(new BoxLayout(myMainContentPanel, BoxLayout.Y_AXIS));
    myEmptyText = new StatusText(myMainContentPanel) {
      @Override
      protected boolean isStatusVisible() {
        return StringUtil.isNotEmpty(getText());
      }
    };

    myMainContentPanel.setOpaque(false);
    myScrollPane.setViewportView(myMainContentPanel);
    myScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    myScrollPane.setViewportBorder(IdeBorderFactory.createEmptyBorder());

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), parent, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      @Override
      public Color getBackground() {
        return getDetailsBackground();
      }
    };
    myLoadingPanel.add(myScrollPane);

    setLayout(new BorderLayout());
    add(myLoadingPanel, BorderLayout.CENTER);

    myEmptyText.setText("Commit details");
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

  public void selectionChanged() {
    int[] rows = myGraphTable.getSelectedRows();

    myLoadingPanel.stopLoading();
    if (rows.length < 1) {
      myEmptyText.setText("No commits selected");
      myMainContentPanel.removeAll();
      return;
    }

    rebuildCommitPanels();
    updateCommitDetails(true);
  }

  public void detailsChanged() {
    myLoadingPanel.stopLoading();
    updateCommitDetails(true);
  }

  public void branchesChanged() {
    updateCommitDetails(false);
  }

  private void updateCommitDetails(boolean requestBranches) {
    int[] rows = myGraphTable.getSelectedRows();
    Set<VcsFullCommitDetails> newCommitDetails = ContainerUtil.newHashSet();
    for (int i = 0; i < Math.min(rows.length, MAX_ROWS); i++) {
      int row = rows[i];
      VcsFullCommitDetails commitData = myGraphTable.getModel().getFullDetails(row);
      CommitPanel commitPanel = getCommitPanel(i);
      commitPanel.setCommit(commitData, requestBranches);
      if (commitData instanceof LoadingDetails) {
        myLoadingPanel.startLoading();
      }
      else {
        newCommitDetails.add(commitData);
      }
    }

    if (!ContainerUtil.intersects(myCurrentCommitDetails, newCommitDetails)) {
      myScrollPane.getVerticalScrollBar().setValue(0);
    }
    myCurrentCommitDetails = newCommitDetails;
  }

  private void rebuildCommitPanels() {
    myEmptyText.setText("");

    int selectionLength = myGraphTable.getSelectedRows().length;

    // for each commit besides the first there are two components: Separator and CommitPanel
    int existingCount = (myMainContentPanel.getComponentCount() + 1) / 2;
    int requiredCount = Math.min(selectionLength, MAX_ROWS);
    for (int i = existingCount; i < requiredCount; i++) {
      if (i > 0) {
        myMainContentPanel.add(new SeparatorComponent(0, OnePixelDivider.BACKGROUND, null));
      }
      myMainContentPanel.add(new CommitPanel());
    }

    // clear superfluous items
    while (myMainContentPanel.getComponentCount() > 2 * requiredCount - 1) {
      myMainContentPanel.remove(myMainContentPanel.getComponentCount() - 1);
    }

    if (selectionLength > MAX_ROWS) {
      myMainContentPanel.add(new SeparatorComponent(0, OnePixelDivider.BACKGROUND, null));
      JBLabel label = new JBLabel("  (showing " + MAX_ROWS + " of " + selectionLength + " selected commits)");
      label.setFont(getDataPanelFont());
      label.setBorder(JBUI.Borders.empty(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2,
                                         myColorManager.isMultipleRoots()
                                         ? VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH +
                                           VcsLogGraphTable.ROOT_INDICATOR_COLORED_WIDTH
                                         : VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2, CommitPanel.BOTTOM_BORDER, 0));
      myMainContentPanel.add(label);
    }

    for (int i = myMainContentPanel.getComponentCount() - 1; i >= 0; i--) {
      ((JComponent)myMainContentPanel.getComponent(i)).setAlignmentX(LEFT_ALIGNMENT);
    }

    repaint();
  }

  @NotNull
  public CommitPanel getCommitPanel(int index) {
    return (CommitPanel)myMainContentPanel.getComponent(2 * index);
  }

  @NotNull
  private static Font getDataPanelFont() {
    return EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
  }

  private class CommitPanel extends JBPanel {
    private static final int BOTTOM_BORDER = 2;
    @NotNull private final ReferencesPanel myReferencesPanel;
    @NotNull private final DataPanel myDataPanel;
    @Nullable private VcsFullCommitDetails myCommit;
    @Nullable private List<String> myBranches;

    public CommitPanel() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setOpaque(false);

      myReferencesPanel = new ReferencesPanel(myColorManager);
      myDataPanel = new DataPanel(myLogData.getProject(), myLogData.isMultiRoot());

      myReferencesPanel.setAlignmentX(LEFT_ALIGNMENT);
      myDataPanel.setAlignmentX(LEFT_ALIGNMENT);

      add(myReferencesPanel);
      add(myDataPanel);
    }

    public void setCommit(@NotNull VcsFullCommitDetails commitData, boolean requestBranches) {
      if (!Comparing.equal(myCommit, commitData)) {
        if (commitData instanceof LoadingDetails) {
          myDataPanel.setData(null);
          myReferencesPanel.setReferences(Collections.emptyList());
          updateBorder(null);
        }
        else {
          myDataPanel.setData(commitData);
          myReferencesPanel.setReferences(sortRefs(commitData.getId(), commitData.getRoot()));
          updateBorder(commitData);
        }
        myCommit = commitData;
      }

      List<String> branches;
      if (!(commitData instanceof LoadingDetails) && requestBranches) {
        branches = myLogData.getContainingBranchesGetter().requestContainingBranches(commitData.getRoot(), commitData.getId());
      }
      else {
        branches = myLogData.getContainingBranchesGetter().getContainingBranchesFromCache(commitData.getRoot(), commitData.getId());
      }

      if (!Comparing.equal(myCommit, commitData) || myBranches != branches) {
        myDataPanel.setBranches(branches);
        myBranches = branches;
      }

      myDataPanel.update();
      revalidate();
    }

    @NotNull
    private List<VcsRef> sortRefs(@NotNull Hash hash, @NotNull VirtualFile root) {
      Collection<VcsRef> refs = myDataPack.getRefs().refsToCommit(hash, root);
      return ContainerUtil.sorted(refs, myLogData.getLogProvider(root).getReferenceManager().getLabelsOrderComparator());
    }

    private void updateBorder(@Nullable VcsFullCommitDetails data) {
      if (data == null || !myColorManager.isMultipleRoots()) {
        setBorder(JBUI.Borders.empty(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2,
                                     VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2, BOTTOM_BORDER, 0));
      }
      else {
        Color color = VcsLogGraphTable.getRootBackgroundColor(data.getRoot(), myColorManager);
        setBorder(new CompoundBorder(new MatteBorder(0, VcsLogGraphTable.ROOT_INDICATOR_COLORED_WIDTH, 0, 0, color),
                                     new MatteBorder(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2,
                                                     VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH, BOTTOM_BORDER, 0,
                                                     new JBColor(DetailsPanel::getDetailsBackground))));
      }
    }

    @Override
    public Color getBackground() {
      return getDetailsBackground();
    }

    public boolean isExpanded() {
      return myDataPanel.isExpanded();
    }
  }

  private static class DataPanel extends JEditorPane {

    private final Project myProject;
    private final boolean myMultiRoot;
    private String myMainText;
    @Nullable
    private List<String> myBranches;
    private boolean myExpanded = false;

    DataPanel(@NotNull Project project, boolean multiRoot) {
      super(UIUtil.HTML_MIME, "");
      myProject = project;
      myMultiRoot = multiRoot;
      setEditable(false);
      setOpaque(false);
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

      DefaultCaret caret = (DefaultCaret)getCaret();
      caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

      addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && LINK_HREF.equals(e.getDescription())) {
            myExpanded = !myExpanded;
            update();
          }
          else {
            BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e);
          }
        }
      });
    }

    @Override
    public void updateUI() {
      super.updateUI();
      update();
    }

    void setData(@Nullable VcsFullCommitDetails commit) {
      if (commit == null) {
        myMainText = null;
      }
      else {
        String header = getHtmlWithFonts(commit.getId().toShortString() + " " + getAuthorText(commit) +
                                         (myMultiRoot ? " [" + commit.getRoot().getName() + "]" : ""));
        String body = getMessageText(commit);
        myMainText = header + "<br/>" + body;
      }
    }

    @NotNull
    private static String getHtmlWithFonts(@NotNull String input) {
      return getHtmlWithFonts(input, getDataPanelFont().getStyle());
    }

    @NotNull
    private static String getHtmlWithFonts(@NotNull String input, int style) {
      return FontUtil.getHtmlWithFonts(input, style, getDataPanelFont());
    }

    void setBranches(@Nullable List<String> branches) {
      if (branches == null) {
        myBranches = null;
      }
      else {
        myBranches = branches;
      }
      myExpanded = false;
    }

    void update() {
      if (myMainText == null) {
        setText("");
      }
      else {
        setText("<html><head>" +
                UIUtil.getCssFontDeclaration(getDataPanelFont()) +
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
        int PER_ROW = 3;
        int rowCount = (int)Math.ceil((double)myBranches.size() / PER_ROW);

        int[] means = new int[PER_ROW - 1];
        int[] max = new int[PER_ROW - 1];

        for (int i = 0; i < rowCount; i++) {
          for (int j = 0; j < PER_ROW - 1; j++) {
            int index = rowCount * j + i;
            if (index < myBranches.size()) {
              means[j] += myBranches.get(index).length();
              max[j] = Math.max(myBranches.get(index).length(), max[j]);
            }
          }
        }
        for (int j = 0; j < PER_ROW - 1; j++) {
          means[j] /= rowCount;
        }

        HtmlTableBuilder builder = new HtmlTableBuilder();
        for (int i = 0; i < rowCount; i++) {
          builder.startRow();
          for (int j = 0; j < PER_ROW; j++) {
            int index = rowCount * j + i;
            if (index >= myBranches.size()) {
              builder.append("");
            }
            else {
              String branch = myBranches.get(index);
              if (index != myBranches.size() - 1) {
                int space = 0;
                if (j < PER_ROW - 1 && branch.length() == max[j]) {
                  space = Math.max(means[j] + 20 - max[j], 5);
                }
                builder.append(branch + StringUtil.repeat("&nbsp;", space), "left");
              }
              else {
                builder.append(branch, "left");
              }
            }
          }

          builder.endRow();
        }

        return "<i>In " + myBranches.size() + " branches:</i> " +
               "<a href=\"" + LINK_HREF + "\"><i>(click to hide)</i></a><br>" +
               builder.build();
      }
      else {
        int TOTAL_MAX = 0;
        int charCount = 0;
        for (String b : myBranches) {
          TOTAL_MAX++;
          charCount += b.length();
          if (charCount >= 50) break;
        }

        String branchText;
        if (myBranches.size() <= TOTAL_MAX) {
          branchText = StringUtil.join(myBranches, ", ");
        }
        else {
          branchText = StringUtil.join(ContainerUtil.getFirstItems(myBranches, TOTAL_MAX), ", ") +
                       "… <a href=\"" +
                       LINK_HREF +
                       "\"><i>(click to show all)</i></a>";
        }
        return "<i>In " + myBranches.size() + StringUtil.pluralize(" branch", myBranches.size()) + ":</i> " + branchText;
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = Math.max(size.height, 4 * getFontMetrics(getFont()).getHeight());
      if (!myExpanded) {
        size.width = 10;
      }
      return size;
    }

    @Override
    public Dimension getMaximumSize() {
      return super.getPreferredSize();
    }

    @NotNull
    private String getMessageText(@NotNull VcsFullCommitDetails commit) {
      String fullMessage = commit.getFullMessage();
      int separator = fullMessage.indexOf("\n\n");
      String subject = separator > 0 ? fullMessage.substring(0, separator) : fullMessage;
      String description = fullMessage.substring(subject.length());
      return "<b>" +
             getHtmlWithFonts(escapeMultipleSpaces(IssueLinkHtmlRenderer.formatTextWithLinks(myProject, subject)), Font.BOLD) +
             "</b>" +
             getHtmlWithFonts(escapeMultipleSpaces(IssueLinkHtmlRenderer.formatTextWithLinks(myProject, description)));
    }

    @NotNull
    private static String escapeMultipleSpaces(@NotNull String text) {
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

    @NotNull
    private static String getAuthorText(@NotNull VcsFullCommitDetails commit) {
      long authorTime = commit.getAuthorTime();
      long commitTime = commit.getCommitTime();

      String authorText = VcsUserUtil.getShortPresentation(commit.getAuthor()) + formatDateTime(authorTime);
      if (!VcsUserUtil.isSamePerson(commit.getAuthor(), commit.getCommitter())) {
        String commitTimeText;
        if (authorTime != commitTime) {
          commitTimeText = formatDateTime(commitTime);
        }
        else {
          commitTimeText = "";
        }
        authorText += " (committed by " + VcsUserUtil.getShortPresentation(commit.getCommitter()) + commitTimeText + ")";
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
    public Dimension getMaximumSize() {
      return super.getPreferredSize();
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
}

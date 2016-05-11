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
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.NotNullProducer;
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
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import com.intellij.vcs.log.util.VcsUserUtil;
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

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JPanel myMainContentPanel;

  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final VcsLogColorManager myColorManager;

  @NotNull private VisiblePack myDataPack;
  @Nullable private VcsFullCommitDetails myCurrentCommitDetails;

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
          if (c instanceof DataPanel && ((DataPanel)c).isExpanded()) {
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

  @Override
  public void valueChanged(@Nullable ListSelectionEvent event) {
    if (event != null && event.getValueIsAdjusting()) return;

    VcsFullCommitDetails newCommitDetails = null;

    int[] rows = myGraphTable.getSelectedRows();

    myLoadingPanel.stopLoading();
    if (rows.length < 1) {
      myEmptyText.setText("No commits selected");
      myMainContentPanel.removeAll();
      return;
    }

    int MAX_ROWS = 50;
    myEmptyText.setText("");
    GraphTableModel tableModel = myGraphTable.getModel();
    int count = 0;
    for (int i = 0; i < Math.min(rows.length, MAX_ROWS); i++) {
      int row = rows[i];
      boolean reuseExisting = count + 1 < myMainContentPanel.getComponentCount();
      ReferencesPanel referencesPanel;
      DataPanel dataPanel;
      if (!reuseExisting) {
        referencesPanel = new ReferencesPanel(myColorManager);
        dataPanel = new DataPanel(myLogData.getProject(), myLogData.isMultiRoot());
        if (i > 0) {
          myMainContentPanel.add(new SeparatorComponent(8, OnePixelDivider.BACKGROUND, null));
          count ++;
        }
        referencesPanel.setAlignmentX(LEFT_ALIGNMENT);
        myMainContentPanel.add(referencesPanel);
        count++;
        dataPanel.setAlignmentX(LEFT_ALIGNMENT);
        myMainContentPanel.add(dataPanel);
        count++;
      }
      else {
        if (i > 0) count ++; // separator
        referencesPanel = (ReferencesPanel)myMainContentPanel.getComponent(count++);
        dataPanel = (DataPanel)myMainContentPanel.getComponent(count++);
      }

      VcsFullCommitDetails commitData = tableModel.getFullDetails(row);
      if (commitData instanceof LoadingDetails) {
        myLoadingPanel.startLoading();
        dataPanel.setData(null);
        referencesPanel.setReferences(Collections.emptyList());
        updateDetailsBorder(null);
      }
      else {
        dataPanel.setData(commitData);
        referencesPanel.setReferences(sortRefs(commitData.getId(), commitData.getRoot()));
        updateDetailsBorder(commitData);
        newCommitDetails = commitData;
      }
      List<String> branches = null;
      if (!(commitData instanceof LoadingDetails)) {
        branches = myLogData.getContainingBranchesGetter().requestContainingBranches(commitData.getRoot(), commitData.getId());
      }
      dataPanel.setBranches(branches);
      dataPanel.update();
    }

    // clear superfluous items
    while (count < myMainContentPanel.getComponentCount()) {
      myMainContentPanel.remove(count);
    }

    if (rows.length > MAX_ROWS) {
      myMainContentPanel.add(new SeparatorComponent(8, OnePixelDivider.BACKGROUND, null));
      JBLabel label = new JBLabel("(showing " + MAX_ROWS + " of " + rows.length + " selected commits)");
      label.setFont(getDataPanelFont());
      label.setAlignmentX(LEFT_ALIGNMENT);
      myMainContentPanel.add(label);
    }

    if (!Comparing.equal(myCurrentCommitDetails, newCommitDetails)) {
      myCurrentCommitDetails = newCommitDetails;
      myScrollPane.getVerticalScrollBar().setValue(0);
    }
  }

  private void updateDetailsBorder(@Nullable VcsFullCommitDetails data) {
    if (data == null || !myColorManager.isMultipleRoots()) {
      myMainContentPanel.setBorder(JBUI.Borders.empty(VcsLogGraphTable.ROOT_INDICATOR_WHITE_WIDTH / 2,
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

  @NotNull
  private List<VcsRef> sortRefs(@NotNull Hash hash, @NotNull VirtualFile root) {
    Collection<VcsRef> refs = myDataPack.getRefs().refsToCommit(hash, root);
    return ContainerUtil.sorted(refs, myLogData.getLogProvider(root).getReferenceManager().getLabelsOrderComparator());
  }

  @NotNull
  private static Font getDataPanelFont() {
    return EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
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
        int rowCount = (int)Math.ceil((double)myBranches.size() / BRANCHES_TABLE_COLUMN_COUNT);

        int[] means = new int[BRANCHES_TABLE_COLUMN_COUNT - 1];
        int[] max = new int[BRANCHES_TABLE_COLUMN_COUNT - 1];

        for (int i = 0; i < rowCount; i++) {
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
                builder.append(branch + StringUtil.repeat("&nbsp;", space), LEFT_ALIGN);
              }
              else {
                builder.append(branch, LEFT_ALIGN);
              }
            }
          }

          builder.endRow();
        }

        return "<i>In " + myBranches.size() + " branches:</i> " +
               "<a href=\"" + SHOW_OR_HIDE_BRANCHES + "\"><i>(click to hide)</i></a><br>" +
               builder.build();
      }
      else {
        String branchText;
        if (myBranches.size() <= BRANCHES_LIMIT) {
          branchText = StringUtil.join(myBranches, ", ");
        }
        else {
          branchText = StringUtil.join(ContainerUtil.getFirstItems(myBranches, BRANCHES_LIMIT), ", ") +
                       "… <a href=\"" +
                       SHOW_OR_HIDE_BRANCHES +
                       "\"><i>(click to show all)</i></a>";
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

    @NotNull
    private String getMessageText(@NotNull VcsFullCommitDetails commit) {
      String fullMessage = commit.getFullMessage();
      int separator = fullMessage.indexOf("\n\n");
      String subject = separator > 0 ? fullMessage.substring(0, separator) : fullMessage;
      String description = fullMessage.substring(subject.length());
      return "<b>" + getHtmlWithFonts(escapeMultipleSpaces(IssueLinkHtmlRenderer.formatTextWithLinks(myProject, subject)), Font.BOLD) + "</b>" +
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

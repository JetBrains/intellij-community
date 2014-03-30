/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.diff.impl.CaptionIcon;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Processor;
import com.intellij.util.containers.Convertor;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.UIVcsUtil;
import git4idea.history.browser.CachedRefs;
import git4idea.history.browser.GitHeavyCommit;
import icons.Git4ideaIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author irengrig
 *         Date: 7/1/11
 *         Time: 2:27 PM
 */
public class GitLogDetailsPanel {
  public static final String CONFIGURE_BRANCHES = "configure_branches";
  private final static String NOTHING_SELECTED = "nothingSelected";
  private final static String LOADING = "loading";
  private final static String DATA = "data";
  private static final String MULTIPLE_SELECTED = "multiple_selected";

  private final JPanel myPanel;
  private JPanel myMarksPanel;

  private final MyPresentationData myPresentationData;

  private final HtmlHighlighter myHtmlHighlighter;
  private JEditorPane myJEditorPane;
  private VirtualFile myRoot;
  private GitHeavyCommit myCommit;
  private final Convertor<VirtualFile, CachedRefs> myRefsProvider;
  private final Processor<AbstractHash> myMarkProcessor;

  public GitLogDetailsPanel(final Project myProject,
                            final DetailsCache detailsCache,
                            final Convertor<VirtualFile, CachedRefs> refsProvider,
                            Processor<AbstractHash> markProcessor) {
    myRefsProvider = refsProvider;
    myMarkProcessor = markProcessor;
    myPanel = new JPanel(new CardLayout());
    myPanel.add(UIVcsUtil.errorPanel("Nothing selected", false), NOTHING_SELECTED);
    myPanel.add(UIVcsUtil.errorPanel("Loading...", false), LOADING);
    myPanel.add(UIVcsUtil.errorPanel("Several commits selected", false), MULTIPLE_SELECTED);

    myHtmlHighlighter = new HtmlHighlighter();
    myPresentationData = new MyPresentationData(myProject, detailsCache, myHtmlHighlighter);

    final JPanel wrapper = new JPanel(new GridBagLayout());
    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                             new Insets(1, 1, 1, 1), 0, 0);

    myJEditorPane = new JEditorPane(UIUtil.HTML_MIME, "");
    myJEditorPane.setPreferredSize(new Dimension(150, 100));
    myJEditorPane.setEditable(false);
    myJEditorPane.setBackground(UIUtil.getComboBoxDisabledBackground());
    myJEditorPane.addHyperlinkListener(new BrowserHyperlinkListener() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (CONFIGURE_BRANCHES.equals(e.getDescription())) {
          if (myRoot == null) return;
          final CachedRefs symbolicRefs = refsProvider.convert(myRoot);
          if (symbolicRefs == null) return;
          final TreeSet<String> localBranches = symbolicRefs.getLocalBranches();
          if (localBranches == null || localBranches.isEmpty()) {
            VcsBalloonProblemNotifier.showOverChangesView(myProject, "Branches is not loaded yet", MessageType.WARNING);
            return;
          }
          final ContainedInBranchesConfigDialog dialog =
            new ContainedInBranchesConfigDialog(myProject, localBranches, symbolicRefs.getRemoteBranches(),
                                                symbolicRefs.getCurrentName(), symbolicRefs.getTrackedRemoteName());
          dialog.show();
          if (dialog.isChanged()) {
            //detailsCache.clearBranches();
            myPresentationData.branchesPresentation();
            changeDetailsText();
          }
          return;
        }
        super.hyperlinkActivated(e);
      }
    });

    myMarksPanel = new JPanel();
    final BoxLayout boxLayout = new BoxLayout(myMarksPanel, BoxLayout.X_AXIS);
    myMarksPanel.setLayout(boxLayout);
    gb.weightx = 1;
    wrapper.add(myMarksPanel, gb);
    ++ gb.gridy;
    gb.weighty = 1;
    gb.fill = GridBagConstraints.BOTH;
    final JBScrollPane tableScroll = new JBScrollPane(myJEditorPane);
    tableScroll.setBorder(null);
    myJEditorPane.setBorder(null);
    wrapper.add(tableScroll, gb);
    myJEditorPane.setBackground(UIUtil.getTableBackground());
    myMarksPanel.setBackground(UIUtil.getTableBackground());
    wrapper.setBackground(UIUtil.getTableBackground());

    myPanel.add(wrapper, DATA);
    ((CardLayout) myPanel.getLayout()).show(myPanel, NOTHING_SELECTED);
  }

  public HtmlHighlighter getHtmlHighlighter() {
    return myHtmlHighlighter;
  }

  public void severalSelected() {
    myPresentationData.clear();
    ((CardLayout) myPanel.getLayout()).show(myPanel, MULTIPLE_SELECTED);
  }

  public void nothingSelected() {
    myPresentationData.clear();
    ((CardLayout) myPanel.getLayout()).show(myPanel, NOTHING_SELECTED);
  }

  public void setBranches(final java.util.List<String> branchesList) {
    myPresentationData.setBranches(branchesList);
    changeDetailsText();
  }

  public void loading(VirtualFile root) {
    myRoot = root;
    myPresentationData.clear();
    ((CardLayout) myPanel.getLayout()).show(myPanel, LOADING);
  }

  public void setData(VirtualFile root, @NotNull final GitHeavyCommit commit) {
    myRoot = root;
    myCommit = commit;
    redrawBranchLabels();

    myPresentationData.setCommit(root, commit);
    ((CardLayout) myPanel.getLayout()).show(myPanel, DATA);

    changeDetailsText();
  }

  private void changeDetailsText() {
    if (myPresentationData.isReady()) {
      myJEditorPane.setText(myPresentationData.getText());
      myPanel.revalidate();
      myPanel.repaint();
    }
  }

  void redrawBranchLabels() {
    final Font tableFont = myJEditorPane.getFont();
    final Font font = tableFont.deriveFont((float)(tableFont.getSize() - 1));
    final String currentBranch = myCommit.getCurrentBranch();
    myMarksPanel.removeAll();
    if (myMarkProcessor.process(myCommit.getShortHash())) {
      myMarksPanel.add(new JLabel(Git4ideaIcons.Star));
    }
    for (String s : myCommit.getLocalBranches()) {
      myMarksPanel.add(new JLabel(new CaptionIcon(GitLogUI.Colors.local, font, s, myMarksPanel, CaptionIcon.Form.SQUARE, false,
                                       s.equals(currentBranch))));
    }
    final String remoteName = myRefsProvider.convert(myRoot).getTrackedRemoteName();
    for (String s : myCommit.getRemoteBranches()) {
      myMarksPanel.add(new JLabel(new CaptionIcon(GitLogUI.Colors.remote, font, s, myMarksPanel, CaptionIcon.Form.SQUARE, false,
                                       s.equals(remoteName))));
    }
    for (String s : myCommit.getTags()) {
      myMarksPanel.add(new JLabel(new CaptionIcon(GitLogUI.Colors.tag, font, s, myMarksPanel, CaptionIcon.Form.ROUNDED, false, false)));
    }
  }

  public JPanel getComponent() {
    return myPanel;
  }

  private static class HtmlHighlighter extends HighlightingRendererBase {
    private final StringBuilder mySb;

    private HtmlHighlighter() {
      super();
      mySb = new StringBuilder();
    }

    @Override
    protected void highlight(String s) {
      mySb.append("<font color=rgb(255,128,0)>").append(s).append("</font>");
    }

    @Override
    protected void usual(String s) {
      mySb.append(s);
    }

    public String getResult(final String text) {
      mySb.setLength(0);
      tryHighlight(text);
      return mySb.toString();
    }
  }

  private static class MyPresentationData {
    private String myStartPattern;
    private final String myEndPattern = "</td></tr></table></body></html>";
    private String myBranches;
    private final Project myProject;
    private final DetailsCache myDetailsCache;
    private final HtmlHighlighter myHighlighter;
    private java.util.List<String> myBranchesList;

    private MyPresentationData(final Project project, final DetailsCache detailsCache, final HtmlHighlighter highlighter) {
      myProject = project;
      myDetailsCache = detailsCache;
      myHighlighter = highlighter;
    }

    public void setCommit(final VirtualFile root, final GitHeavyCommit c) {
      final String hash = myHighlighter.getResult(c.getHash().getValue());
      final String author = myHighlighter.getResult(c.getAuthor());
      final String committer = myHighlighter.getResult(c.getCommitter());
      final String comment = IssueLinkHtmlRenderer.formatTextWithLinks(myProject, c.getDescription(),
                                                                       new Convertor<String, String>() {
                                                                         @Override
                                                                         public String convert(String o) {
                                                                           return myHighlighter.getResult(o);
                                                                         }
                                                                       });

      final StringBuilder sb = new StringBuilder().append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
        .append("</head><body><table>");
      final String stashName = myDetailsCache.getStashName(root, c.getShortHash());
      if (! StringUtil.isEmptyOrSpaces(stashName)) {
        sb.append("<tr valign=\"top\"><td><b>").append(stashName).append("</b></td><td></td></tr>");
      }
      sb.append("<tr valign=\"top\"><td><i>Hash:</i></td><td>").append(
        hash).append("</td></tr>" + "<tr valign=\"top\"><td><i>Author:</i></td><td>")
        .append(author).append(" (").append(c.getAuthorEmail()).append(") <i>at</i> ")
        .append(DateFormatUtil.formatPrettyDateTime(c.getAuthorTime()))
        .append("</td></tr>" + "<tr valign=\"top\"><td><i>Committer:</i></td><td>")
        .append(committer).append(" (").append(c.getComitterEmail()).append(") <i>at</i> ")
        .append(DateFormatUtil.formatPrettyDateTime(c.getDate())).append(
        "</td></tr>" + "<tr valign=\"top\"><td><i>Description:</i></td><td><b>")
        .append(comment).append("</b></td></tr>");
      sb.append("<tr valign=\"top\"><td><i>Contained in branches:</i><br/><a href=\"" + CONFIGURE_BRANCHES + "\">Configure</a></td><td>");
      myStartPattern = sb.toString();
    }

    public void setBranches(final java.util.List<String> branches) {
      myBranchesList = branches;
      branchesPresentation();
    }

    public void branchesPresentation() {
      final GitLogSettings settings = GitLogSettings.getInstance(myProject);
      final Set<String> locals = settings.getLocalBranchesCopy();
      final Set<String> remotes = settings.getRemoteBranchesCopy();

      final StringBuilder sb = new StringBuilder();
      if (myBranchesList != null && (! myBranchesList.isEmpty())) {
        for (int i = 0; i < myBranchesList.size(); i++) {
          String s = myBranchesList.get(i);
          final boolean contained = locals.contains(s) || remotes.contains(s);
          if ((contained || settings.isHighlight()) && sb.length() > 0) {
            sb.append(", ");
          }
          if (contained) {
            if (settings.isHighlight()) {
              sb.append("<b>").append(s).append("</b>");
            } else {
              sb.append(s);
            }
          } else {
            if (settings.isHighlight()) {
              sb.append(s);
            }
          }
        }
        myBranches = sb.toString();
      } else if (myBranchesList != null && myBranchesList.isEmpty()) {
        myBranches = "<font color=gray>&lt;no branches&gt;</font><br/>";
      } else {
        myBranches = "<font color=gray>Loading...</font><br/>";
      }
    }

    protected String getBranches() {
      return myBranches == null ? "<font color=gray>Loading...</font>" : myBranches;
    }

    public boolean isReady() {
      return myStartPattern != null;
    }

    public String getText() {
      return myStartPattern + getBranches() + myEndPattern;
    }

    public void clear() {
      myBranches = null;
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.List;

import static com.intellij.util.text.DateFormatUtil.formatPrettyDateTime;

public class CommittedChangeListRenderer extends ColoredTreeCellRenderer {
  private final IssueLinkRenderer myRenderer;
  private final List<? extends CommittedChangeListDecorator> myDecorators;
  private final Project myProject;
  private int myDateWidth;
  private int myFontSize;

  public CommittedChangeListRenderer(final Project project, final List<? extends CommittedChangeListDecorator> decorators) {
    myProject = project;
    myRenderer = new IssueLinkRenderer(project, this);
    myDecorators = decorators;
    myDateWidth = 0;
    myFontSize = -1;
  }

  @NotNull
  public static String getDescriptionOfChangeList(@NotNull String text) {
    return text.replaceAll("\n", " // ");
  }

  @Contract(pure = true)
  public static @NotNull String truncateDescription(@NotNull String initDescription, @NotNull FontMetrics fontMetrics, int maxWidth) {
    String description = initDescription;
    int descWidth = fontMetrics.stringWidth(description);
    while (description.length() > 0 && (descWidth > maxWidth)) {
      description = trimLastWord(description);
      descWidth = fontMetrics.stringWidth(description + " ");
    }
    return description;
  }

  @Override
  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    customize(tree, value, selected, expanded, leaf, row, hasFocus);
  }

  public void customize(JComponent tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    if (userObject instanceof CommittedChangeList) {
      CommittedChangeList changeList = (CommittedChangeList)userObject;
      renderChangeList(tree, changeList);
    }
    else if (userObject instanceof String) {
      append((String)userObject, //NON-NLS See CommittedChangesTreeBrowser.buildTreeModel
             SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  public void renderChangeList(JComponent tree, CommittedChangeList changeList) {
    final Container parent = tree.getParent();
    final int rowX = getRowX(myTree, 2);
    int availableWidth = parent == null ? 100 : parent.getWidth() - rowX;
    final FontMetrics fontMetrics = tree.getFontMetrics(tree.getFont());
    final FontMetrics boldMetrics = tree.getFontMetrics(tree.getFont().deriveFont(Font.BOLD));
    final FontMetrics italicMetrics = tree.getFontMetrics(tree.getFont().deriveFont(Font.ITALIC));
    if (myDateWidth <= 0 || (fontMetrics.getFont().getSize() != myFontSize)) {
      myDateWidth = Math.max(fontMetrics.stringWidth(", Yesterday 00:00 PM "), // NON-NLS
                             fontMetrics.stringWidth(", 00/00/00 00:00 PM ")); // NON-NLS
      myDateWidth = Math.max(myDateWidth, fontMetrics.stringWidth(formatPrettyDateTime(DateFormatUtil.getSampleDateTime())));
      myFontSize = fontMetrics.getFont().getSize();
    }
    int dateCommitterSize = myDateWidth + boldMetrics.stringWidth(changeList.getCommitterName());

    String description = getDescriptionOfChangeList(changeList.getName().trim());

    for (CommittedChangeListDecorator decorator : myDecorators) {
      final Icon icon = decorator.decorate(changeList);
      if (icon != null) {
        setIcon(icon);
      }
    }

    int descMaxWidth = availableWidth - dateCommitterSize - 8;
    boolean partial = (changeList instanceof ReceivedChangeList) && ((ReceivedChangeList)changeList).isPartial();
    int descWidth = 0;
    int partialMarkerWidth = 0;
    if (partial) {
      final String partialMarker = VcsBundle.message("committed.changes.partial.list") + " ";
      append(partialMarker, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      partialMarkerWidth = boldMetrics.stringWidth(partialMarker);
      descWidth += partialMarkerWidth;
    }

    descWidth += fontMetrics.stringWidth(description);

    int numberWidth = 0;
    final AbstractVcs vcs = changeList.getVcs();
    if (vcs != null) {
      final CachingCommittedChangesProvider provider = vcs.getCachingCommittedChangesProvider();
      if (provider != null && provider.getChangelistTitle() != null) {
        String number = "#" + changeList.getNumber() + "  ";
        numberWidth = fontMetrics.stringWidth(number);
        descWidth += numberWidth;
        append(number, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }

    int branchWidth = 0;
    String branch = changeList.getBranch();
    if (branch != null) {
      branch += " ";
      branchWidth = italicMetrics.stringWidth(branch);
      descWidth += branchWidth;
      append(branch, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }

    if (description.isEmpty()) {
      append(VcsBundle.message("committed.changes.empty.comment"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      appendTextPadding(descMaxWidth);
    }
    else if (descMaxWidth < 0) {
      myRenderer.appendTextWithLinks(description);
    }
    else if (descWidth < descMaxWidth) {
      myRenderer.appendTextWithLinks(description);
      appendTextPadding(descMaxWidth);
    }
    else {
      final String moreMarker = VcsBundle.message("changes.browser.details.marker");
      int moreWidth = fontMetrics.stringWidth(moreMarker);
      int remainingWidth = descMaxWidth - moreWidth - numberWidth - branchWidth - partialMarkerWidth;
      description = truncateDescription(description, fontMetrics, remainingWidth);
      myRenderer.appendTextWithLinks(description);
      if (!StringUtil.isEmpty(description)) {
        append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        append(moreMarker, SimpleTextAttributes.LINK_ATTRIBUTES, new CommittedChangesTreeBrowser.MoreLauncher(myProject, changeList));
      } else if (remainingWidth > 0) {
        append(moreMarker, SimpleTextAttributes.LINK_ATTRIBUTES, new CommittedChangesTreeBrowser.MoreLauncher(myProject, changeList));
      }
      // align value is for the latest added piece
      appendTextPadding(descMaxWidth);
    }

    append(changeList.getCommitterName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    if (changeList.getCommitDate() != null) {
      append(", " + formatPrettyDateTime(changeList.getCommitDate()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private static String trimLastWord(final String description) {
    int pos = description.trim().lastIndexOf(' ');
    if (pos >= 0) {
      return description.substring(0, pos).trim();
    }
    return description.substring(0, description.length()-1);
  }

  public static int getRowX(JTree tree, int depth) {
    if (tree == null) return 0;
    final TreeUI ui = tree.getUI();
    if (ui instanceof BasicTreeUI) {
      final BasicTreeUI treeUI = ((BasicTreeUI)ui);
      return (treeUI.getLeftChildIndent() + treeUI.getRightChildIndent()) * depth;
    }

    final int leftIndent = UIUtil.getTreeLeftChildIndent();
    final int rightIndent = UIUtil.getTreeRightChildIndent();

    return (leftIndent + rightIndent) * depth;
  }
}

/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.text.DateFormat;
import java.util.Date;

public class CommittedChangeListRenderer extends ColoredTreeCellRenderer {
  private final static DateFormat myDateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
  private static final SimpleTextAttributes LINK_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, Color.blue);
  private final IssueLinkRenderer myRenderer;
  private final java.util.List<CommittedChangeListDecorator> myDecorators;
  private final Project myProject;

  public CommittedChangeListRenderer(final Project project, final java.util.List<CommittedChangeListDecorator> decorators) {
    myProject = project;
    myRenderer = new IssueLinkRenderer(project, this);
    myDecorators = decorators;
  }

  public static String getDateOfChangeList(final Date date) {
    return myDateFormat.format(date);
  }

  public static Pair<String, Boolean> getDescriptionOfChangeList(final String text) {
    String description = text;
    int pos = description.indexOf("\n");
    if (pos >= 0) {
      description = description.substring(0, pos).trim();
      return new Pair<String, Boolean>(description, Boolean.TRUE);
    }
    return new Pair<String, Boolean>(description, Boolean.FALSE);
  }

  public static String truncateDescription(final String initDescription, final FontMetrics fontMetrics, int maxWidth) {
    String description = initDescription;
    int descWidth = fontMetrics.stringWidth(description);
    while(description.length() > 0 && (descWidth > maxWidth)) {
      description = trimLastWord(description);
      descWidth = fontMetrics.stringWidth(description + " ");
    }
    return description;
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    customize(tree, value, selected, expanded, leaf, row, hasFocus);
  }

  public void customize(JComponent tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
    if (node.getUserObject() instanceof CommittedChangeList) {
      CommittedChangeList changeList = (CommittedChangeList) node.getUserObject();

      renderChangeList(tree, changeList);
    }
    else if (node.getUserObject() != null) {
      append(node.getUserObject().toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  public void renderChangeList(JComponent tree, CommittedChangeList changeList) {
    final Container parent = tree.getParent();
    int parentWidth = parent == null ? 100 : parent.getWidth() - 74;
    String date = ", " + getDateOfChangeList(changeList.getCommitDate());
    final FontMetrics fontMetrics = tree.getFontMetrics(tree.getFont());
    final FontMetrics boldMetrics = tree.getFontMetrics(tree.getFont().deriveFont(Font.BOLD));
    int size = fontMetrics.stringWidth(date);
    size += boldMetrics.stringWidth(changeList.getCommitterName());

    final Pair<String, Boolean> descriptionInfo = getDescriptionOfChangeList(changeList.getName().trim());
    boolean truncated = descriptionInfo.getSecond().booleanValue();
    String description = descriptionInfo.getFirst();

    for (CommittedChangeListDecorator decorator : myDecorators) {
      final Icon icon = decorator.decorate(changeList);
      if (icon != null) {
        setIcon(icon);
      }
    }

    int descMaxWidth = parentWidth - size - 8;
    boolean partial = (changeList instanceof ReceivedChangeList) && ((ReceivedChangeList)changeList).isPartial();
    if (partial) {
      final String partialMarker = VcsBundle.message("committed.changes.partial.list") + " ";
      append(partialMarker, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      descMaxWidth -= boldMetrics.stringWidth(partialMarker);
    }

    int descWidth = fontMetrics.stringWidth(description);

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

    if (description.length() == 0 && !truncated) {
      append(VcsBundle.message("committed.changes.empty.comment"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      appendAlign(parentWidth - size);
    }
    else if (descMaxWidth < 0) {
      myRenderer.appendTextWithLinks(description);
    }
    else if (descWidth < descMaxWidth && !truncated) {
      myRenderer.appendTextWithLinks(description);
      appendAlign(parentWidth - size);
    }
    else {
      final String moreMarker = VcsBundle.message("changes.browser.details.marker");
      int moreWidth = fontMetrics.stringWidth(moreMarker);
      description = truncateDescription(description, fontMetrics, (descMaxWidth - moreWidth - numberWidth));
      myRenderer.appendTextWithLinks(description);
      // we don't have place for changelist number in this case
      final int addWidth = fontMetrics.stringWidth(description + " ");
      append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(moreMarker, LINK_ATTRIBUTES, new CommittedChangesTreeBrowser.MoreLauncher(myProject, changeList));
      // align value is for the latest added piece
      appendAlign(parentWidth - size - addWidth);
    }

    append(changeList.getCommitterName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    append(date, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  private void appendDescriptionAndNumber(final String description, final String number) {
    myRenderer.appendTextWithLinks(description);
    if (number != null) {
      append(number, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private static String trimLastWord(final String description) {
    int pos = description.trim().lastIndexOf(' ');
    if (pos >= 0) {
      return description.substring(0, pos).trim();
    }
    return description.substring(0, description.length()-1);
  }

  public Dimension getPreferredSize() {
    return new Dimension(2000, super.getPreferredSize().height);
  }
}

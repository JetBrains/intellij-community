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
package com.intellij.usages.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DarculaColors;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * @author max
 */
class UsageViewTreeCellRenderer extends ColoredTreeCellRenderer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.UsageViewTreeCellRenderer");
  private static final EditorColorsScheme ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
  private static final SimpleTextAttributes ourInvalidAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.INVALID_PREFIX));
  private static final SimpleTextAttributes ourReadOnlyAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.READONLY_PREFIX));
  private static final SimpleTextAttributes ourNumberOfUsagesAttribute = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES));
  private static final SimpleTextAttributes ourInvalidAttributesDarcula = new SimpleTextAttributes(null, DarculaColors.RED, null, ourInvalidAttributes.getStyle());
  public static final Insets STANDARD_IPAD_NOWIFI = new Insets(1, 2, 1, 2);
  private static final Rectangle EMPTY_RECTANGLE = new Rectangle();
  private boolean myRowBoundsCalled = false;

  private final UsageViewPresentation myPresentation;
  private final UsageView myView;

  UsageViewTreeCellRenderer(@NotNull UsageView view) {
    myView = view;
    myPresentation = view.getPresentation();
  }

  private Dimension cachedPreferredSize;

  @Override
  public void customizeCellRenderer(@Nullable JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    boolean showAsReadOnly = false;
    if (value instanceof Node && tree != null && value != tree.getModel().getRoot()) {
      Node node = (Node)value;
      if (!node.isValid()) {
        append(UsageViewBundle.message("node.invalid") + " ", UIUtil.isUnderDarcula() ? ourInvalidAttributesDarcula : ourInvalidAttributes);
      }
      if (myPresentation.isShowReadOnlyStatusAsRed() && node.isReadOnly()) {
        showAsReadOnly = true;
      }
    }

    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      Object userObject = treeNode.getUserObject();

      Rectangle visibleRect = tree == null ? EMPTY_RECTANGLE : ((JViewport)tree.getParent()).getViewRect();
      if (!visibleRect.isEmpty()) {
        //Protection against SOE on some OSes and JDKs IDEA-120631
        RowLocation visible = myRowBoundsCalled ? RowLocation.INSIDE_VISIBLE_RECT : isRowVisible(row, visibleRect);
        myRowBoundsCalled = false;
        if (visible != RowLocation.INSIDE_VISIBLE_RECT) {
          // for the node outside visible rect just set its preferred size to the whole visible rect
          // and do not compute (expensive) presentation
          setIpad(new Insets(1,visibleRect.width, 1, 0));
          return;
        }
        if (!getIpad().equals(STANDARD_IPAD_NOWIFI)) {
          // for the visible node, return its ipad to the standard value
          setIpad(STANDARD_IPAD_NOWIFI);
        }
      }

      if (userObject instanceof UsageTarget) {
        UsageTarget usageTarget = (UsageTarget)userObject;
        if (!usageTarget.isValid()) {
          append(UsageViewBundle.message("node.invalid"), ourInvalidAttributes);
          return;
        }

        final ItemPresentation presentation = usageTarget.getPresentation();
        LOG.assertTrue(presentation != null);
        if (showAsReadOnly) {
          append(UsageViewBundle.message("node.readonly") + " ", ourReadOnlyAttributes);
        }
        final String text = presentation.getPresentableText();
        append(text == null ? "" : text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(presentation.getIcon(expanded));
      }
      else if (treeNode instanceof GroupNode) {
        GroupNode node = (GroupNode)treeNode;

        if (node.isRoot()) {
          append(StringUtil.capitalize(myPresentation.getUsagesWord()), patchAttrs(node, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
        }
        else {
          append(node.getGroup().getText(myView),
                 patchAttrs(node, showAsReadOnly ? ourReadOnlyAttributes : SimpleTextAttributes.REGULAR_ATTRIBUTES));
          setIcon(node.getGroup().getIcon(expanded));
        }

        int count = node.getRecursiveUsageCount();
        append(" (" + StringUtil.pluralize(count + " " + myPresentation.getUsagesWord(), count) + ")",
               patchAttrs(node, ourNumberOfUsagesAttribute));
      }
      else if (treeNode instanceof UsageNode) {
        UsageNode node = (UsageNode)treeNode;
        setIcon(node.getUsage().getPresentation().getIcon());
        if (showAsReadOnly) {
          append(UsageViewBundle.message("node.readonly") + " ", patchAttrs(node, ourReadOnlyAttributes));
        }

        if (node.isValid()) {
          TextChunk[] text = node.getUsage().getPresentation().getText();
          for (TextChunk textChunk : text) {
            SimpleTextAttributes simples = textChunk.getSimpleAttributesIgnoreBackground();
            append(textChunk.getText(), patchAttrs(node, simples));
          }
        }
      }
      else if (userObject instanceof String) {
        append((String)userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      else {
        append(userObject == null ? "" : userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
    else {
      append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    if (tree != null) {
      SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, mySelected);
    }
  }

  // computes the node text regardless of the node visibility
  @NotNull
  public String getPlainTextForNode(Object value) {
    boolean showAsReadOnly = false;
    StringBuilder result = new StringBuilder();
    if (value instanceof Node) {
      Node node = (Node)value;
      if (!node.isValid()) {
        result.append(UsageViewBundle.message("node.invalid")).append(" ");
      }
      if (myPresentation.isShowReadOnlyStatusAsRed() && node.isReadOnly()) {
        showAsReadOnly = true;
      }
    }

    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      Object userObject = treeNode.getUserObject();

      if (userObject instanceof UsageTarget) {
        UsageTarget usageTarget = (UsageTarget)userObject;
        if (usageTarget.isValid()) {
          final ItemPresentation presentation = usageTarget.getPresentation();
          LOG.assertTrue(presentation != null);
          if (showAsReadOnly) {
            result.append(UsageViewBundle.message("node.readonly")).append(" ");
          }
          final String text = presentation.getPresentableText();
          result.append(text == null ? "" : text);
        }
        else {
          result.append(UsageViewBundle.message("node.invalid"));
        }
      }
      else if (treeNode instanceof GroupNode) {
        GroupNode node = (GroupNode)treeNode;

        if (node.isRoot()) {
          result.append(StringUtil.capitalize(myPresentation.getUsagesWord()));
        }
        else {
          result.append(node.getGroup().getText(myView));
        }

        int count = node.getRecursiveUsageCount();
        result.append(" (" + StringUtil.pluralize(count + " " + myPresentation.getUsagesWord(), count) + ")");
      }
      else if (treeNode instanceof UsageNode) {
        UsageNode node = (UsageNode)treeNode;

        if (showAsReadOnly) {
          result.append(UsageViewBundle.message("node.readonly")).append(" ");
        }

        if (node.isValid()) {
          TextChunk[] text = node.getUsage().getPresentation().getText();
          for (TextChunk textChunk : text) {
            result.append(textChunk.getText());
          }
        }
      }
      else if (userObject instanceof String) {
        result.append((String)userObject);
      }
      else {
        result.append(userObject == null ? "" : userObject.toString());
      }
    }
    else {
      result.append(value);
    }
    return result.toString();
  }

  enum RowLocation {
    BEFORE_VISIBLE_RECT, INSIDE_VISIBLE_RECT, AFTER_VISIBLE_RECT
  }
  @NotNull
  public RowLocation isRowVisible(int row, @NotNull Rectangle visibleRect) {
    Dimension pref;
    if (cachedPreferredSize == null) {
      cachedPreferredSize = pref = getPreferredSize();
    }
    else {
      pref = cachedPreferredSize;
    }
    pref.width = Math.max(visibleRect.width, pref.width);
    myRowBoundsCalled = true;
    JTree tree = getTree();
    final Rectangle bounds = tree == null ? null : tree.getRowBounds(row);
    myRowBoundsCalled = false;
    int y = bounds == null ? 0 : bounds.y;
    TextRange vis = TextRange.from(Math.max(0, visibleRect.y - pref.height), visibleRect.height + pref.height * 2);
    boolean inside = vis.contains(y);
    if (inside) {
      return RowLocation.INSIDE_VISIBLE_RECT;
    }
    return y < vis.getStartOffset() ? RowLocation.BEFORE_VISIBLE_RECT : RowLocation.AFTER_VISIBLE_RECT;
  }

  private static SimpleTextAttributes patchAttrs(@NotNull Node node, @NotNull SimpleTextAttributes original) {
    if (node.isExcluded()) {
      original = new SimpleTextAttributes(original.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, original.getFgColor(), original.getWaveColor());
    }
    if (node instanceof GroupNode) {
      UsageGroup group = ((GroupNode)node).getGroup();
      FileStatus fileStatus = group != null ? group.getFileStatus() : null;
      if (fileStatus != null && fileStatus != FileStatus.NOT_CHANGED) {
        original = new SimpleTextAttributes(original.getStyle(), fileStatus.getColor(), original.getWaveColor());
      }

      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
      if (parent != null && parent.isRoot()) {
        original = new SimpleTextAttributes(original.getStyle() | SimpleTextAttributes.STYLE_BOLD, original.getFgColor(), original.getWaveColor());
      }
    }
    return original;
  }

  static String getTooltipFromPresentation(final Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      if (treeNode instanceof UsageNode) {
        UsageNode node = (UsageNode)treeNode;
        return node.getUsage().getPresentation().getTooltipText();
      }
    }
    return null;
  }
}

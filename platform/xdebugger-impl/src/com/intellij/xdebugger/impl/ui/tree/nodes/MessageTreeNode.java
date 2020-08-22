// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.tree.TreeNode;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageTreeNode extends XDebuggerTreeNode {
  private final boolean myEllipsis;
  private XDebuggerTreeNodeHyperlink myLink;

  private MessageTreeNode(XDebuggerTree tree, @Nullable final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes,
                          @Nullable Icon icon) {
    this(tree, parent, message, attributes, icon, null);
  }

  private MessageTreeNode(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes,
                          @Nullable Icon icon, final XDebuggerTreeNodeHyperlink link) {
    this(tree, parent, message, attributes, icon, false, link);
  }

  private MessageTreeNode(XDebuggerTree tree, final XDebuggerTreeNode parent, final String message, final SimpleTextAttributes attributes,
                          @Nullable Icon icon, final boolean ellipsis, final XDebuggerTreeNodeHyperlink link) {
    super(tree, parent, true);
    myEllipsis = ellipsis;
    myLink = link;
    setIcon(icon);
    myText.append(message, attributes);
  }

  protected MessageTreeNode(XDebuggerTree tree, @Nullable XDebuggerTreeNode parent, boolean leaf) {
    super(tree, parent, leaf);
    myEllipsis = false;
  }

  @NotNull
  @Override
  public List<? extends TreeNode> getChildren() {
    return Collections.emptyList();
  }

  public boolean isEllipsis() {
    return myEllipsis;
  }

  @Nullable
  @Override
  public XDebuggerTreeNodeHyperlink getLink() {
    return myLink;
  }

  @NotNull
  @Override
  public List<? extends XDebuggerTreeNode> getLoadedChildren() {
    return Collections.emptyList();
  }

  @Override
  public void clearChildren() {
  }

  public static MessageTreeNode createEllipsisNode(XDebuggerTree tree, XDebuggerTreeNode parent, final int remaining) {
    String message = remaining == -1 ? XDebuggerBundle.message("node.text.ellipsis.0.unknown.more.nodes.double.click.to.show")
                                     : XDebuggerBundle.message("node.text.ellipsis.0.more.nodes.double.click.to.show", remaining);
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.GRAYED_ATTRIBUTES, null, true, null);
  }

  public static MessageTreeNode createMessageNode(XDebuggerTree tree, XDebuggerTreeNode parent, String message, @Nullable Icon icon) {
    return new MessageTreeNode(tree, parent, message, SimpleTextAttributes.REGULAR_ATTRIBUTES, icon);
  }

  public static MessageTreeNode createLoadingMessage(XDebuggerTree tree, final XDebuggerTreeNode parent) {
    return new MessageTreeNode(tree, parent, XDebuggerUIConstants.getCollectingDataMessage(),
                               XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static MessageTreeNode createEvaluatingMessage(XDebuggerTree tree, @Nullable XDebuggerTreeNode parent) {
    return new MessageTreeNode(tree, parent, XDebuggerUIConstants.getEvaluatingExpressionMessage(),
                               XDebuggerUIConstants.EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES, null);
  }

  public static List<MessageTreeNode> createMessages(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull String errorMessage,
                                                     XDebuggerTreeNodeHyperlink link,
                                                     final Icon icon, final SimpleTextAttributes attributes) {
    List<MessageTreeNode> messages = new SmartList<>();
    final List<String> lines = StringUtil.split(errorMessage, "\n", true, false);
    for (int i = 0; i < lines.size(); i++) {
      messages.add(new MessageTreeNode(tree, parent, lines.get(i), attributes,
                                       icon, i == lines.size() - 1 ? link : null));
    }
    return messages;
  }

  public static MessageTreeNode createInfoMessage(XDebuggerTree tree, @NotNull String message) {
    return createInfoMessage(tree, message, null);
  }

  public static MessageTreeNode createInfoMessage(XDebuggerTree tree, @NotNull String message, @Nullable HyperlinkListener hyperlinkListener) {
    Matcher matcher = MessageTreeNodeWithLinks.HREF_PATTERN.matcher(message);
    if (hyperlinkListener == null || !matcher.find()) {
      return new MessageTreeNode(tree, null, message, SimpleTextAttributes.REGULAR_ATTRIBUTES,
                                 XDebuggerUIConstants.INFORMATION_MESSAGE_ICON);
    }

    List<Object> objects = new ArrayList<>();
    int prev = 0;
    do {
      if (matcher.start() != prev) {
        objects.add(message.substring(prev, matcher.start()));
      }
      objects.add(new HyperlinkListenerDelegator(matcher.group(2), matcher.group(1), hyperlinkListener));
      prev = matcher.end();
    }
    while (matcher.find());

    if (prev < message.length()) {
      objects.add(message.substring(prev));
    }
    return new MessageTreeNodeWithLinks(tree, objects);
  }

  private static final class MessageTreeNodeWithLinks extends MessageTreeNode {
    private static final Pattern HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>");
    private final List<Object> objects;

    private MessageTreeNodeWithLinks(XDebuggerTree tree, List<Object> objects) {
      super(tree, null, true);
      setIcon(XDebuggerUIConstants.INFORMATION_MESSAGE_ICON);
      this.objects = objects;
    }

    @Override
    public void appendToComponent(@NotNull ColoredTextContainer component) {
      for (Object object : objects) {
        if (object instanceof String) {
          component.append((String)object, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          XDebuggerTreeNodeHyperlink hyperlink = (XDebuggerTreeNodeHyperlink)object;
          component.append(hyperlink.getLinkText(), SimpleTextAttributes.LINK_ATTRIBUTES, hyperlink);
        }
      }
    }
  }

  public static final class HyperlinkListenerDelegator extends XDebuggerTreeNodeHyperlink {
    private final HyperlinkListener hyperlinkListener;
    private final String href;

    public HyperlinkListenerDelegator(@NotNull String linkText, @Nullable String href, @NotNull HyperlinkListener hyperlinkListener) {
      super(linkText);

      this.hyperlinkListener = hyperlinkListener;
      this.href = href;
    }

    @Override
    public void onClick(MouseEvent event) {
      hyperlinkListener.hyperlinkUpdate(IJSwingUtilities.createHyperlinkEvent(href, getLinkText()));
    }
  }
}

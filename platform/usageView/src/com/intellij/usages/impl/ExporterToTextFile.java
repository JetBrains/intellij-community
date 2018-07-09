/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageViewSettings;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;

/**
 * @author max
 */
public class ExporterToTextFile implements com.intellij.ide.ExporterToTextFile {
  private final UsageViewImpl myUsageView;
  @NotNull
  private final UsageViewSettings myUsageViewSettings;

  public ExporterToTextFile(@NotNull UsageViewImpl usageView, @NotNull UsageViewSettings usageViewSettings) {
    myUsageView = usageView;
    myUsageViewSettings = usageViewSettings;
  }

  @NotNull
  @Override
  public String getReportText() {
    StringBuilder buf = new StringBuilder();
    appendNode(buf, myUsageView.getModelRoot(), SystemProperties.getLineSeparator(), "");
    return buf.toString();
  }

  private void appendNode(StringBuilder buf, DefaultMutableTreeNode node, String lineSeparator, String indent) {
    buf.append(indent);
    final String childIndent;
    if (node.getParent() != null) {
      childIndent = indent + "    ";
      appendNodeText(buf, node, lineSeparator);
    }
    else {
      childIndent = indent;
    }

    Enumeration enumeration = node.children();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)enumeration.nextElement();
      appendNode(buf, child, lineSeparator, childIndent);
    }
  }

  private void appendNodeText(StringBuilder buf, DefaultMutableTreeNode node, String lineSeparator) {
    if (node instanceof Node && ((Node)node).isExcluded()) {
      buf.append("(").append(UsageViewBundle.message("usage.excluded")).append(") ");
    }

    if (node instanceof UsageNode) {
      appendUsageNodeText(buf, (UsageNode) node);
    }
    else if (node instanceof GroupNode) {
      UsageGroup group = ((GroupNode)node).getGroup();
      buf.append(group != null ? group.getText(myUsageView) : UsageViewBundle.message("usages.title"));
      buf.append(" ");
      int count = ((GroupNode)node).getRecursiveUsageCount();
      buf.append(" (").append(UsageViewBundle.message("usages.n", count)).append(")");
    }
    else if (node instanceof UsageTargetNode) {
      buf.append(((UsageTargetNode)node).getTarget().getPresentation().getPresentableText());
    }
    else {
      buf.append(node.toString());
    }
    buf.append(lineSeparator);
  }

  protected void appendUsageNodeText(StringBuilder buf, UsageNode node) {
    TextChunk[] chunks = node.getUsage().getPresentation().getText();
    int chunkCount = 0;
    for (TextChunk chunk : chunks) {
      if (chunkCount == 1) buf.append(" "); // add space after line number
      buf.append(chunk.getText());
      ++chunkCount;
    }
  }

  @NotNull
  @Override
  public String getDefaultFilePath() {
    return myUsageViewSettings.getExportFileName();
  }

  @Override
  public void exportedTo(@NotNull String filePath) {
    myUsageViewSettings.setExportFileName(filePath);
  }

  @Override
  public boolean canExport() {
    return !myUsageView.isSearchInProgress() && myUsageView.areTargetsValid();
  }
}

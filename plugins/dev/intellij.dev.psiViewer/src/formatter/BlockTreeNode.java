// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class BlockTreeNode extends SimpleNode {
  private final Block myBlock;

  BlockTreeNode(Block block, BlockTreeNode parent) {
    super(parent);
    myBlock = block;
  }

  public Block getBlock() {
    return myBlock;
  }

  @Override
  public BlockTreeNode @NotNull [] getChildren() {
    return ContainerUtil.map2Array(myBlock.getSubBlocks(), BlockTreeNode.class, block -> new BlockTreeNode(block, this));
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    @NlsSafe String name = myBlock.getDebugName();
    if (name == null) name = myBlock.getClass().getSimpleName();
    if (myBlock instanceof DataLanguageBlockWrapper wrapper) {
      name += " (" + wrapper.getOriginal().getClass().getSimpleName() + ")";
    }
    presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (myBlock.getIndent() != null) {
      presentation.addText(" " + String.valueOf(myBlock.getIndent()).replaceAll("[<>]", " "), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    if (myBlock.getAlignment() != null) {
      float d = 1.f * System.identityHashCode(myBlock.getAlignment()) / Integer.MAX_VALUE;
      Color color = new JBColor(Color.HSBtoRGB(d, .3f, .7f),
                                Color.HSBtoRGB(d, .3f, .8f));
      presentation
        .addText(" " + myBlock.getAlignment(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color));
    }
    if (myBlock.getWrap() != null) {
      presentation
        .addText(" " + myBlock.getWrap(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, PlatformColors.BLUE));
    }
  }

  @Override
  public Object @NotNull [] getEqualityObjects() {
    return new Object[]{myBlock};
  }

  @Override
  public boolean isAlwaysLeaf() {
    return myBlock.isLeaf() && myBlock.getSubBlocks().isEmpty();
  }
}

package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestBlock implements Block{
  private TextRange myTextRange;

  private final List<Object> myElements = new ArrayList<>();
  private Wrap myWrap;
  private Indent myIndent;
  private Alignment myAlignment;
  private String myText = "";
  private boolean myIsIncomplete = false;

  public TestBlock(final TextRange textRange) {
    myTextRange = textRange;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myTextRange;
  }

  @Override
  @NotNull
  public List<Block> getSubBlocks() {
    return getBlockList();
  }

  public String toString() {
    return myText;
  }

  private List<Block> getBlockList() {
    final ArrayList<Block> blocks = new ArrayList<>();
    for (Object o : myElements) {
      if (o instanceof Block) blocks.add((Block)o);
    }
    return blocks;
  }

  @Override
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  public TestBlock setWrap(final Wrap wrap) {
    myWrap = wrap;
    return this;
  }

  public TestBlock setAlignment(final Alignment alignment) {
    myAlignment = alignment;
    return this;
  }

  public TestBlock setIndent(final Indent indent) {
    myIndent = indent;
    return this;
  }

  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    if (child1 == null) {
      return null;
    }
    final int index = myElements.indexOf(child2);
    if (myElements.get(index - 1) instanceof Spacing) {
      return (Spacing)myElements.get(index - 1);
    }
    return null;
  }

  public void addBlock(final Block block) {
    myElements.add(block);
  }

  public void setTextRange(final TextRange textRange) {
    myTextRange = textRange;
  }

  public void addSpace(final Spacing spacing) {
    myElements.add(spacing);
  }

  public void setText(final String s) {
    myText = s;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return myIsIncomplete;
  }

  @Override
  public boolean isLeaf() {
    return myElements.isEmpty();
  }

  public void setIsIncomplete(final boolean value) {
    myIsIncomplete = value;
  }
}

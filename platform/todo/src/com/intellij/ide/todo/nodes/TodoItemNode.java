// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.HighlightedRegionProvider;
import com.intellij.ide.todo.SmartTodoItemPointer;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoItem;
import com.intellij.psi.search.TodoPattern;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TodoItemNode extends BaseToDoNode<SmartTodoItemPointer> implements HighlightedRegionProvider {
  private static final Logger LOG = Logger.getInstance(TodoItem.class);

  public TodoItemNode(Project project,
                      @NotNull SmartTodoItemPointer value,
                      TodoTreeBuilder builder) {
    super(project, value, builder);
    RangeMarker rangeMarker = value.getRangeMarker();
    LOG.assertTrue(rangeMarker.isValid());
  }

  @Override
  protected @NotNull PresentationData createPresentation() {
    return new TodoItemNodePresentationData();
  }

  @Override
  public boolean contains(Object element) {
    return canRepresent(element);
  }

  @Override
  public boolean canRepresent(Object element) {
    SmartTodoItemPointer value = getValue();
    TodoItem item = value != null ? value.getTodoItem() : null;
    return Comparing.equal(item, element);
  }

  @Override
  public int getFileCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public int getTodoItemCount(final SmartTodoItemPointer val) {
    return 1;
  }

  @Override
  public List<HighlightedRegion> getHighlightedRegions() {
    return Collections.unmodifiableList(((TodoItemNodePresentationData)getPresentation()).getHighlightedRegions());
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    SmartTodoItemPointer todoItemPointer = getValue();
    assert todoItemPointer != null;
    TodoItem todoItem = todoItemPointer.getTodoItem();
    RangeMarker myRangeMarker = todoItemPointer.getRangeMarker();
    if (!todoItem.getFile().isValid() || !myRangeMarker.isValid() || myRangeMarker.getStartOffset() == myRangeMarker.getEndOffset()) {
      myRangeMarker.dispose();
      setValue(null);
      return;
    }

    @NotNull List<HighlightedRegion> myHighlightedRegions = ((TodoItemNodePresentationData) presentation).getHighlightedRegions();
    @NotNull List<HighlightedRegionProvider> myAdditionalLines = ((TodoItemNodePresentationData) presentation).getAdditionalLines();
    myHighlightedRegions.clear();
    myAdditionalLines.clear();

    // Update name

    Document document = todoItemPointer.getDocument();
    CharSequence chars = document.getCharsSequence();
    int startOffset = myRangeMarker.getStartOffset();
    int endOffset = myRangeMarker.getEndOffset();
    int lineNumber = document.getLineNumber(startOffset);
    int lineStartOffset = document.getLineStartOffset(lineNumber);

    // skip all white space characters

    while (lineStartOffset < document.getTextLength() && (chars.charAt(lineStartOffset) == '\t' || chars.charAt(lineStartOffset) == ' ')) {
      lineStartOffset++;
    }

    int lineEndOffset = document.getLineEndOffset(lineNumber);

    String lineColumnPrefix = String.valueOf(lineNumber + 1) + " ";
    String highlightedText = chars.subSequence(lineStartOffset, Math.min(lineEndOffset, chars.length())).toString();

    String newName = lineColumnPrefix + highlightedText;

    // Update icon

    TodoPattern pattern = todoItem.getPattern();
    Icon newIcon = pattern != null ? pattern.getAttributes().getIcon() : null;

    // Update highlighted regions

    myHighlightedRegions.clear();
    EditorHighlighter highlighter = myBuilder.getHighlighter(todoItem.getFile(), document);
    collectHighlights(myHighlightedRegions, highlighter, lineStartOffset, lineEndOffset, lineColumnPrefix.length());
    TextAttributes attributes =
      pattern != null ? pattern.getAttributes().getTextAttributes() : TodoAttributesUtil.getDefaultColorSchemeTextAttributes();
    myHighlightedRegions.add(new HighlightedRegion(
      0,
      lineColumnPrefix.length(),
      UsageTreeColors.NUMBER_OF_USAGES_ATTRIBUTES.toTextAttributes()
    ));
    myHighlightedRegions.add(new HighlightedRegion(
      lineColumnPrefix.length() + startOffset - lineStartOffset,
      lineColumnPrefix.length() + endOffset - lineStartOffset,
      attributes
    ));

    //

    presentation.setPresentableText(newName);
    presentation.setIcon(newIcon);

    for (RangeMarker additionalMarker : todoItemPointer.getAdditionalRangeMarkers()) {
      if (!additionalMarker.isValid()) break;
      ArrayList<HighlightedRegion> highlights = new ArrayList<>();
      int lineNum = document.getLineNumber(additionalMarker.getStartOffset());
      int lineStart = document.getLineStartOffset(lineNum);
      int lineEnd = document.getLineEndOffset(lineNum);
      int lineStartNonWs = CharArrayUtil.shiftForward(chars, lineStart, " \t");
      if (lineStartNonWs > additionalMarker.getStartOffset() || lineEnd < additionalMarker.getEndOffset()) {
        // can happen for an invalid (obsolete) node, tree implementation can call this method for such a node
        break;
      }
      collectHighlights(highlights, highlighter, lineStartNonWs, lineEnd, 0);
      highlights.add(new HighlightedRegion(
        additionalMarker.getStartOffset() - lineStartNonWs,
        additionalMarker.getEndOffset() - lineStartNonWs,
        attributes
      ));
      myAdditionalLines.add(new AdditionalTodoLine(document.getText(new TextRange(lineStartNonWs, lineEnd)), highlights));
    }
  }

  private static void collectHighlights(@NotNull List<? super HighlightedRegion> highlights, @NotNull EditorHighlighter highlighter,
                                        int startOffset, int endOffset, int highlightOffsetShift) {
    HighlighterIterator iterator = highlighter.createIterator(startOffset);
    while (!iterator.atEnd()) {
      int start = Math.max(iterator.getStart(), startOffset);
      int end = Math.min(iterator.getEnd(), endOffset);
      if (start >= endOffset) break;

      TextAttributes attributes = iterator.getTextAttributes();
      int fontType = attributes.getFontType();
      if ((fontType & Font.BOLD) != 0) { // suppress bold attribute
        attributes = attributes.clone();
        attributes.setFontType(fontType & ~Font.BOLD);
      }
      HighlightedRegion region = new HighlightedRegion(
        highlightOffsetShift + start - startOffset,
        highlightOffsetShift + end - startOffset,
        attributes
      );
      highlights.add(region);
      iterator.advance();
    }
  }

  @Override
  public String getTestPresentation() {
    return "Item: " + getValue().getTodoItem().getTextRange();
  }

  @Override
  public int getWeight() {
    return 5;
  }

  public @NotNull List<HighlightedRegionProvider> getAdditionalLines() {
    return Collections.unmodifiableList(((TodoItemNodePresentationData)getPresentation()).getAdditionalLines());
  }

  private static final class AdditionalTodoLine implements HighlightedRegionProvider {
    private final String myText;
    private final List<HighlightedRegion> myHighlights;

    private AdditionalTodoLine(String text, List<HighlightedRegion> highlights) {
      myText = text;
      myHighlights = highlights;
    }

    @Override
    public Iterable<HighlightedRegion> getHighlightedRegions() {
      return myHighlights;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}

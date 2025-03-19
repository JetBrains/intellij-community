// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @see InlayModel#addAfterLineEndElement
 */
final class AfterLineEndInlayImpl<R extends EditorCustomElementRenderer> extends InlayImpl<R, AfterLineEndInlayImpl<?>> {
  private static int ourGlobalCounter = 0;
  final boolean mySoftWrappable;
  final int myPriority;
  final int myOrder;

  AfterLineEndInlayImpl(@NotNull EditorImpl editor,
                        int offset,
                        boolean relatesToPrecedingText,
                        boolean softWrappable,
                        int priority,
                        @NotNull R renderer) {
    super(editor, offset, relatesToPrecedingText, renderer);
    mySoftWrappable = softWrappable;
    myPriority = priority;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    myOrder = ourGlobalCounter++;
  }

  @Override
  @ApiStatus.Internal
  public RangeMarkerTree<AfterLineEndInlayImpl<?>> getTree() {
    return myEditor.getInlayModel().myAfterLineEndElementsTree;
  }

  @Override
  void doUpdate() {
    myWidthInPixels = myRenderer.calcWidthInPixels(this);
    if (myWidthInPixels <= 0) {
      throw PluginException.createByClass("Positive width should be defined for an after-line-end element by " + myRenderer, null,
                                          myRenderer.getClass());
    }
  }

  @Override
  Point getPosition() {
    //  at com.intellij.util.concurrency.ThreadingAssertions.createThreadAccessException(ThreadingAssertions.java:212)
    //	at com.intellij.util.concurrency.ThreadingAssertions.softAssertReadAccess(ThreadingAssertions.java:151)
    //	at com.intellij.openapi.application.impl.ApplicationImpl.assertReadAccessAllowed(ApplicationImpl.java:1009)
    //	at com.intellij.openapi.editor.impl.FoldingModelImpl.assertReadAccess(FoldingModelImpl.java:215)
    //	at com.intellij.openapi.editor.impl.FoldingModelImpl.isOffsetCollapsed(FoldingModelImpl.java:192)
    //	at com.intellij.openapi.editor.impl.AfterLineEndInlayImpl.getVisualPosition(AfterLineEndInlayImpl.java:66)
    //	at com.intellij.openapi.editor.impl.AfterLineEndInlayImpl.getPosition(AfterLineEndInlayImpl.java:51)
    VisualPosition pos = ReadAction.compute(() -> getVisualPosition());
    return myEditor.visualPositionToXY(pos);
  }

  @Override
  public @NotNull Placement getPlacement() {
    return Placement.AFTER_LINE_END;
  }

  @Override
  public @NotNull VisualPosition getVisualPosition() {
    int offset = getOffset();
    int logicalLine = myEditor.getDocument().getLineNumber(offset);
    int lineEndOffset = myEditor.getDocument().getLineEndOffset(logicalLine);
    VisualPosition position = myEditor.offsetToVisualPosition(lineEndOffset, true, true);
    if (myEditor.getFoldingModel().isOffsetCollapsed(lineEndOffset)) return position;
    List<Inlay<?>> inlays = myEditor.getInlayModel().getAfterLineEndElementsForLogicalLine(logicalLine);
    int order = inlays.indexOf(this);
    return new VisualPosition(position.line, position.column + 1 + order);
  }

  @Override
  public int getHeightInPixels() {
    return myEditor.getLineHeight();
  }

  @Override
  public @NotNull InlayProperties getProperties() {
    return new InlayProperties()
      .relatesToPrecedingText(isRelatedToPrecedingText())
      .disableSoftWrapping(!mySoftWrappable)
      .priority(myPriority);
  }

  @Override
  public String toString() {
    return "[After-line-end inlay, offset=" + getOffset() + ", width=" + myWidthInPixels + ", renderer=" + myRenderer + "]" + (isValid() ? "" : "(invalid)");
  }
}

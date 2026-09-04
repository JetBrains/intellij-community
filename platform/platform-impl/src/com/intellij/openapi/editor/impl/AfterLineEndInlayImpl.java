// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.InlayModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @see InlayModel#addAfterLineEndElement
 */
final class AfterLineEndInlayImpl<R extends EditorCustomElementRenderer>
  extends InlayImpl<R, AfterLineEndInlayImpl<?>> implements AfterLineEndInlay<R> {
  private static int ourGlobalCounter;
  private final boolean mySoftWrappable;
  private final int myPriority;
  private final int myOrder;

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
    return myEditor.getInlayModel().getAfterLineEndElementsTree();
  }

  @Override
  public boolean isSoftWrappable() {
    return mySoftWrappable;
  }

  @Override
  public int getPriority() {
    return myPriority;
  }

  @Override
  public int getOrder() {
    return myOrder;
  }

  @Override
  public String toString() {
    return "[After-line-end inlay, offset=" + getOffset() + ", width=" + myWidthInPixels + ", renderer=" + myRenderer + "]" + (isValid() ? "" : "(invalid)");
  }
}

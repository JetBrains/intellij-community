// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.CustomWrapModel;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapParsingListener;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.Graphics;
import java.beans.PropertyChangeListener;
import java.util.List;

// Temporarily, to make the transition for IJPL-156498 safer, we keep two implementations.
// LegacySoftWrapModelImpl is the original battle-tested implementation mostly unchanged.
// ExperimentalSoftWrapModelImpl adds support for custom soft wraps.
//@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract public class SoftWrapModelImpl extends InlayModel.SimpleAdapter
  implements SoftWrapModelEx, PrioritizedDocumentListener, FoldingListener,
             PropertyChangeListener, Dumpable, Disposable, CustomWrapModel.Listener
{
  static @NotNull SoftWrapModelImpl create(@NotNull EditorImpl editor) {
    return Registry.is("editor.custom.soft.wraps.support.enabled") ?
           new ExperimentalSoftWrapModelImpl(editor) :
           new LegacySoftWrapModelImpl(editor);
  }

  @ApiStatus.Internal
  public abstract boolean shouldSoftWrapsBeForced();

  /**
   * Called on editor settings change. Current model is expected to drop all cached information about the settings if any.
   */
  @ApiStatus.Internal
  public abstract void reinitSettings();

  @Override
  public final @Nullable SoftWrap getSoftWrap(int offset) {
    return getSoftWrapEx(offset);
  }

  @ApiStatus.Internal
  public abstract @Nullable SoftWrapEx getSoftWrapEx(int offset);

  /**
   * @return    total number of soft wrap-introduced new visual lines
   */
  @ApiStatus.Internal
  public abstract int getSoftWrapsIntroducedLinesNumber();

  @Override
  public final List<? extends SoftWrap> getRegisteredSoftWraps() {
    return getRegisteredSoftWrapsEx();
  }

  @ApiStatus.Internal
  public abstract @NotNull List<? extends SoftWrapEx> getRegisteredSoftWrapsEx();

  @ApiStatus.Internal
  public abstract int doPaint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight);

  /**
   * Encapsulates preparations for performing document dimension mapping (e.g., visual to logical position) and answers
   * if soft wraps-aware processing should be used (e.g., there is no need to consider soft wraps if user configured them
   * not to be used).
   */
  @ApiStatus.Internal
  public abstract void prepareToMapping();

  @ApiStatus.Internal
  public abstract boolean addSoftWrapParsingListener(@NotNull SoftWrapParsingListener listener);

  @ApiStatus.Internal
  public abstract boolean removeSoftWrapParsingListener(@NotNull SoftWrapParsingListener listener);

  abstract void onBulkDocumentUpdateStarted();

  abstract void onBulkDocumentUpdateFinished();

  abstract void recalculate();

  public abstract SoftWrapApplianceManager getApplianceManager();

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public abstract void setSoftWrapPainter(SoftWrapPainter painter);

  public abstract boolean isDirty();

  @TestOnly
  abstract void validateState();

  @ApiStatus.Internal
  public abstract void customWrapsMerged();
}

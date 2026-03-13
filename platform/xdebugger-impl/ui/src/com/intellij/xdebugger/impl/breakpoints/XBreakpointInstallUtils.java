// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy;
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange;
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInstallationInfo;
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy;
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ModalityUiUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.FrontendXLineBreakpointVariant;
import com.intellij.xdebugger.impl.FrontendXLineBreakpointVariantKt;
import com.intellij.xdebugger.impl.VariantChoiceData;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.ui.DebuggerColors;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class XBreakpointInstallUtils {
  private static final Logger LOG = Logger.getInstance(XBreakpointInstallUtils.class);
  private static final Ref<Boolean> SHOW_BREAKPOINT_AD = new Ref<>(true);

  private XBreakpointInstallUtils() { }

  public static @NotNull CompletableFuture<@Nullable XLineBreakpointProxy> toggleAndReturnLineBreakpointProxy(
    final @NotNull Project project,
    @NotNull List<XLineBreakpointTypeProxy> types,
    final @NotNull XSourcePosition position,
    boolean selectVariantByPositionColumn,
    final boolean temporary,
    final @Nullable Editor editor,
    boolean canRemove,
    boolean isLogging,
    @Nullable String logExpression
  ) {
    var breakpointInfo = new XLineBreakpointInstallationInfo(types, position, temporary, isLogging, logExpression, canRemove);
    return toggleAndReturnLineBreakpointProxy(project, editor, breakpointInfo, selectVariantByPositionColumn);
  }

  public static @NotNull CompletableFuture<@Nullable XLineBreakpointProxy> toggleAndReturnLineBreakpointProxy(
    @NotNull Project project,
    @Nullable Editor editor,
    @NotNull XLineBreakpointInstallationInfo breakpointInfo,
    boolean selectVariantByPositionColumn
  ) {
    if (XDebuggerUtil.areInlineBreakpointsEnabled(breakpointInfo.getPosition().getFile())) {
      return processInlineBreakpoints(project, editor, breakpointInfo, selectVariantByPositionColumn);
    }
    else {
      return selectBreakpointVariantWithPopup(project, breakpointInfo, editor);
    }
  }

  private static @NotNull CompletableFuture<@Nullable XLineBreakpointProxy> selectBreakpointVariantWithPopup(
    @NotNull Project project,
    @NotNull XLineBreakpointInstallationInfo breakpointInfo,
    @Nullable Editor editor
  ) {
    final VirtualFile file = breakpointInfo.getPosition().getFile();
    final int line = breakpointInfo.getPosition().getLine();
    var breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project);

    for (XLineBreakpointTypeProxy type : breakpointInfo.getTypes()) {
      XLineBreakpointProxy breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
      if (breakpoint != null) {
        return XBreakpointUIUtil.removeBreakpointIfPossible(breakpointInfo, breakpoint).thenApply(v -> null);
      }
    }
    return FrontendXLineBreakpointVariantKt.computeBreakpointProxy(project, editor, breakpointInfo, variantChoice -> {
      assert !variantChoice.getVariants().isEmpty();
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
        for (XLineBreakpointTypeProxy type : breakpointInfo.getTypes()) {
          if (breakpointManager.findBreakpointAtLine(type, file, line) != null) {
            variantChoice.breakpointRemoved();
            return;
          }
        }
        RelativePoint relativePoint = editor != null ? DebuggerUIUtil.getPositionForPopup(editor, line) : null;
        if (variantChoice.getVariants().size() > 1 && relativePoint != null) {
          showBreakpointSelectionPopup(project, breakpointInfo.getPosition(), editor, variantChoice, relativePoint);
        }
        else {
          variantChoice.select(variantChoice.getVariants().get(0));
        }
      });
      return Unit.INSTANCE;
    });
  }

  private static void showBreakpointSelectionPopup(
    @NotNull Project project,
    @NotNull XSourcePosition position,
    @NotNull Editor editor,
    VariantChoiceData choiceData,
    RelativePoint relativePoint
  ) {
    final int line = position.getLine();
    class MySelectionListener implements ListSelectionListener {
      RangeHighlighter myHighlighter = null;

      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          updateHighlighter(((JList<?>)e.getSource()).getSelectedValue());
        }
      }

      public void initialSet(Object value) {
        if (myHighlighter == null) {
          updateHighlighter(value);
        }
      }

      void updateHighlighter(Object value) {
        clearHighlighter();
        if (value instanceof FrontendXLineBreakpointVariant variant) {
          TextRange range = variant.getHighlightRange();
          TextRange lineRange = DocumentUtil.getLineTextRange(editor.getDocument(), line);
          if (range == null) {
            range = lineRange;
          }
          if (!range.isEmpty() && range.intersectsStrict(lineRange)) {
            myHighlighter = editor.getMarkupModel().addRangeHighlighter(
              DebuggerColors.BREAKPOINT_ATTRIBUTES, range.getStartOffset(), range.getEndOffset(),
              DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER,
              HighlighterTargetArea.EXACT_RANGE);
          }
        }
      }

      private void clearHighlighter() {
        if (myHighlighter != null) {
          myHighlighter.dispose();
        }
      }
    }

    final int defaultIndex = getIndexOfBestMatchingInlineVariant(position.getOffset(), choiceData.getVariants());

    final MySelectionListener selectionListener = new MySelectionListener();
    BaseListPopupStep<FrontendXLineBreakpointVariant> step =
      new BaseListPopupStep<>(XDebuggerBundle.message("popup.title.set.breakpoint"), choiceData.getVariants()) {
        @Override
        public @NotNull String getTextFor(FrontendXLineBreakpointVariant value) {
          return value.getText();
        }

        @Override
        public Icon getIconFor(FrontendXLineBreakpointVariant value) {
          return value.getIcon();
        }

        @Override
        public void canceled() {
          selectionListener.clearHighlighter();
          choiceData.cancel();
        }

        @Override
        public PopupStep<?> onChosen(FrontendXLineBreakpointVariant selectedValue, boolean finalChoice) {
          selectionListener.clearHighlighter();
          choiceData.select(selectedValue);
          return FINAL_CHOICE;
        }

        @Override
        public int getDefaultOptionIndex() {
          return defaultIndex;
        }
      };
    ListPopupImpl popup = new ListPopupImpl(project, step) {
      @Override
      protected void afterShow() {
        super.afterShow();
        selectionListener.initialSet(getList().getSelectedValue());
      }
    };
    DebuggerUIUtil.registerExtraHandleShortcuts(popup, SHOW_BREAKPOINT_AD, IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT);

    popup.addListSelectionListener(selectionListener);
    popup.show(relativePoint);
  }

  private static @NotNull CompletableFuture<@Nullable XLineBreakpointProxy> processInlineBreakpoints(
    @NotNull Project project,
    @Nullable Editor editor,
    @NotNull XLineBreakpointInstallationInfo breakpointInfo,
    boolean selectVariantByPositionColumn
  ) {
    return FrontendXLineBreakpointVariantKt.computeBreakpointProxy(project, editor, breakpointInfo, variantChoice -> {
      var variants = variantChoice.getVariants().stream().filter(v -> v.getUseAsInlineVariant()).toList();
      if (variants.isEmpty()) {
        LOG.error("Unexpected empty variants");
        variantChoice.cancel();
        return Unit.INSTANCE;
      }

      List<XLineBreakpointProxy> breakpoints = XBreakpointUIUtil.findBreakpointsAtLine(project, breakpointInfo);

      FrontendXLineBreakpointVariant variant;
      if (selectVariantByPositionColumn) {
        Object breakpointOrVariant = getBestMatchingBreakpoint(breakpointInfo.getPosition().getOffset(),
                                                               Stream.concat(breakpoints.stream(), variants.stream()).iterator(),
                                                               o ->
                                                                 o instanceof XLineBreakpointProxy b ? rangeOrNull(b.getHighlightRange())
                                                                                                     : ((FrontendXLineBreakpointVariant)o).getHighlightRange());

        if (breakpointOrVariant instanceof XLineBreakpointProxy existingBreakpoint) {
          XBreakpointUIUtil.removeBreakpointIfPossible(breakpointInfo, existingBreakpoint)
            .thenRun(variantChoice::breakpointRemoved);
          return Unit.INSTANCE;
        }

        variant = (FrontendXLineBreakpointVariant)breakpointOrVariant;
      }
      else {
        if (!breakpoints.isEmpty()) {
          XBreakpointUIUtil.removeBreakpointIfPossible(breakpointInfo, breakpoints.toArray(XLineBreakpointProxy[]::new))
            .thenRun(variantChoice::breakpointRemoved);
          return Unit.INSTANCE;
        }

        variant = variants.stream().max(Comparator.comparing(v -> v.getPriority())).get();
      }

      variantChoice.select(variant);
      return Unit.INSTANCE;
    });
  }

  private static int getIndexOfBestMatchingInlineVariant(int caretOffset, List<? extends FrontendXLineBreakpointVariant> variants) {
    assert !variants.isEmpty();
    TextRange bestRange = null;
    int bestIndex = -1;
    for (int i = 0; i < variants.size(); i++) {
      var variant = variants.get(i);
      TextRange range = variant.getHighlightRange();
      if (range != null && range.contains(caretOffset)) {
        if (bestRange == null || bestRange.getLength() > range.getLength()) {
          bestRange = range;
          bestIndex = i;
        }
      }
    }
    // Use first variant if nothing interesting is found.
    return bestIndex == -1 ? 0 : bestIndex;
  }

  private static <T> @NotNull T getBestMatchingBreakpoint(int caretOffset,
                                                          Iterator<@NotNull T> breakpoints,
                                                          Function<T, @Nullable TextRange> rangeProvider) {
    // Best matching = closest to the insertion point and minimal by range of all breakpoints or breakpoint variants
    T bestBreakpoint = null;
    int bestDistance = Integer.MAX_VALUE;
    int bestRangeLength = Integer.MAX_VALUE;
    while (breakpoints.hasNext()) {
      var b = breakpoints.next();
      TextRange range = rangeProvider.apply(b);
      int rangeLength = range != null ? range.getLength() : Integer.MAX_VALUE;
      // note that range = null means "whole line"
      int distance = range == null ?
                     0 :
                     range.containsOffset(caretOffset) ? //include end offset
                     0 :
                     Math.min(Math.abs(range.getStartOffset() - caretOffset), Math.abs(range.getEndOffset() - caretOffset));
      if (bestBreakpoint == null || distance < bestDistance || (distance == bestDistance && rangeLength < bestRangeLength)) {
        bestBreakpoint = b;
        bestDistance = distance;
        bestRangeLength = rangeLength;
      }
    }
    assert bestBreakpoint != null;
    return bestBreakpoint;
  }

  private static TextRange rangeOrNull(XLineBreakpointHighlighterRange range) {
    if (range instanceof XLineBreakpointHighlighterRange.Available available) {
      return available.getRange();
    }
    return null;
  }
}

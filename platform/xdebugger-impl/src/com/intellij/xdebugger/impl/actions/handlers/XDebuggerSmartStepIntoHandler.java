// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.AppMode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint;
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHintKt;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Hint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListActions;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import com.intellij.xdebugger.ui.DebuggerColors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public class XDebuggerSmartStepIntoHandler extends XDebuggerSuspendedActionHandler {
  private static final Ref<Boolean> SHOW_AD = new Ref<>(true);
  private static final Logger LOG = Logger.getInstance(XDebuggerSmartStepIntoHandler.class);
  private static final String COUNTER_PROPERTY = "debugger.smart.chooser.counter";

  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    return super.isEnabled(session, dataContext) && session.getDebugProcess().getSmartStepIntoHandler() != null;
  }

  @Override
  protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    XSmartStepIntoHandler<?> handler = session.getDebugProcess().getSmartStepIntoHandler();
    XSourcePosition position = session.getTopFramePosition();
    if (position != null && handler != null) {
      FileEditor editor = FileEditorManager.getInstance(session.getProject()).getSelectedEditor(position.getFile());
      if (editor instanceof TextEditor) {
        doSmartStepInto(handler, position, session, ((TextEditor)editor).getEditor());
        return;
      }
    }
    session.stepInto();
  }

  private <V extends XSmartStepIntoVariant> void doSmartStepInto(final XSmartStepIntoHandler<V> handler,
                                                                 XSourcePosition position,
                                                                 final XDebugSession session,
                                                                 Editor editor) {
    SmartStepData stepData = editor.getUserData(SMART_STEP_INPLACE_DATA);
    if (stepData != null) {
      stepData.stepInto(stepData.myCurrentVariant);
    }
    else {
      computeVariants(handler, position)
        .onSuccess(variants -> UIUtil.invokeLaterIfNeeded(() -> {
          if (!handleSimpleCases(handler, variants, session)) {
            choose(handler, variants, position, session, editor);
          }
        }))
        .onError(throwable -> UIUtil.invokeLaterIfNeeded(session::stepInto));
    }
  }

  protected <V extends XSmartStepIntoVariant> Promise<List<V>> computeVariants(XSmartStepIntoHandler<V> handler, XSourcePosition position) {
    return handler.computeSmartStepVariantsAsync(position);
  }

  protected <V extends XSmartStepIntoVariant> boolean handleSimpleCases(XSmartStepIntoHandler<V> handler,
                                                                        List<? extends V> variants,
                                                                        XDebugSession session) {
    if (variants.isEmpty()) {
      handler.stepIntoEmpty(session);
      return true;
    }
    else if (variants.size() == 1) {
      session.smartStepInto(handler, variants.get(0));
      return true;
    }
    return false;
  }

  private static <V extends XSmartStepIntoVariant> void choose(final XSmartStepIntoHandler<V> handler,
                                                               List<? extends V> variants,
                                                               XSourcePosition position,
                                                               final XDebugSession session,
                                                               Editor editor) {
    if (Registry.is("debugger.smart.step.inplace") && ContainerUtil.and(variants, v -> v.getHighlightRange() != null)) {
      try {
        inplaceChoose(handler, variants, session, editor);
        return;
      } catch (Exception e) {
        // in case of any exception fallback to popup
        LOG.error(e);
        SmartStepData data = editor.getUserData(SMART_STEP_INPLACE_DATA);
        if (data != null) {
          data.clear();
        }
      }
    }
    showPopup(handler, variants, position, session, editor);
  }

  private static <V extends XSmartStepIntoVariant> void showPopup(final XSmartStepIntoHandler<V> handler,
                                                                    List<? extends V> variants,
                                                                    XSourcePosition position,
                                                                    final XDebugSession session,
                                                                    Editor editor) {
    ScopeHighlighter highlighter = new ScopeHighlighter(editor);
    ListPopupImpl popup = new ListPopupImpl(session.getProject(), new BaseListPopupStep<V>(handler.getPopupTitle(position), variants) {
      @Override
      public Icon getIconFor(V aValue) {
        return aValue.getIcon();
      }

      @NotNull
      @Override
      public String getTextFor(V value) {
        return value.getText();
      }

      @Override
      public PopupStep<?> onChosen(V selectedValue, boolean finalChoice) {
        session.smartStepInto(handler, selectedValue);
        highlighter.dropHighlight();
        return FINAL_CHOICE;
      }

      @Override
      public void canceled() {
        highlighter.dropHighlight();
        super.canceled();
      }
    });

    DebuggerUIUtil.registerExtraHandleShortcuts(popup, SHOW_AD, XDebuggerActions.STEP_INTO, XDebuggerActions.SMART_STEP_INTO);
    UIUtil.maybeInstall(popup.getList().getInputMap(JComponent.WHEN_FOCUSED),
                        ListActions.Down.ID,
                        KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));

    popup.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          Object selectedValue = ObjectUtils.doIfCast(e.getSource(), JBList.class, it -> it.getSelectedValue());
          highlightVariant(ObjectUtils.tryCast(selectedValue, XSmartStepIntoVariant.class), highlighter);
        }
      }
    });
    highlightVariant(ObjectUtils.tryCast(ContainerUtil.getFirstItem(variants), XSmartStepIntoVariant.class), highlighter);
    DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine());
  }

  private static void highlightVariant(@Nullable XSmartStepIntoVariant variant, @NotNull ScopeHighlighter highlighter) {
    TextRange range = variant != null ? variant.getHighlightRange() : null;
    if (range != null) {
      highlighter.highlight(Pair.create(range, Collections.singletonList(range)));
    }
  }

  private static <V extends XSmartStepIntoVariant> void inplaceChoose(XSmartStepIntoHandler<V> handler,
                                                                      List<? extends V> variants,
                                                                      XDebugSession session,
                                                                      Editor editor) {
    HighlightManager highlightManager = HighlightManager.getInstance(session.getProject());

    SmartStepData<V> data = new SmartStepData<>(handler, variants, session, editor);

    EditorHyperlinkSupport hyperlinkSupport = EditorHyperlinkSupport.get(editor);
    for (SmartStepData<V>.VariantInfo info : data.myVariants) {
      TextRange range = info.myVariant.getHighlightRange();
      if (range != null) {
        List<RangeHighlighter> highlighters = new SmartList<>();
        highlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), DebuggerColors.SMART_STEP_INTO_TARGET,
                                                HighlightManager.HIDE_BY_ESCAPE | HighlightManager.HIDE_BY_TEXT_CHANGE, highlighters);
        RangeHighlighter highlighter = highlighters.get(0);
        hyperlinkSupport.createHyperlink(highlighter, project -> data.stepInto(info));
        data.myHighlighters.add(highlighter);
      }
    }

    data.myVariants.stream().filter(v -> v.myVariant == variants.get(0)).findFirst().ifPresent(data::select);
    LOG.assertTrue(data.myCurrentVariant != null);
    editor.putUserData(SMART_STEP_INPLACE_DATA, data);
    // for the remote development scenario we have to add a fake invisible highlighter on the whole document with extra payload
    // that will be restored on the client and used to alternate actions availability
    // see com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHintKt.addActionAvailabilityHint
    ((MarkupModelEx)editor.getMarkupModel()).addRangeHighlighterAndChangeAttributes(HighlighterColors.NO_HIGHLIGHTING, 0, editor.getDocument().getTextLength(),
                                                                                    HighlighterLayer.LAST, HighlighterTargetArea.EXACT_RANGE, false, h -> {
      // this hints should be added in this lambda in order to be serialized by RD markup machinery
      EditorActionAvailabilityHintKt.addActionAvailabilityHint(h,
                                                               new EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_ENTER, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
                                                               new EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_TAB, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
                                                               new EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_ESCAPE, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
                                                               new EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
                                                               new EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
                                                               new EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
                                                               new EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside));
      data.myActionHintSyntheticHighlighter = h;
    });

    session.updateExecutionPosition();
    if (AppMode.isRemoteDevHost()) {
      VirtualFile virtualFile = editor.getVirtualFile();
      // in the case of remote development the ordinary focus request doesn't work, we need to use FileEditorManagerEx api to focus the editor
      if (virtualFile != null) {
        FileEditorManagerEx.getInstanceEx(session.getProject()).openFile(virtualFile, true, true);
      }
    }
    IdeFocusManager.getGlobalInstance().requestFocus(editor.getContentComponent(), true);
    showInfoHint(editor, data);

    session.addSessionListener(new XDebugSessionListener() {
      void onAnyEvent() {
        session.removeSessionListener(this);
        UIUtil.invokeLaterIfNeeded(() -> {
          SmartStepData stepData = editor.getUserData(SMART_STEP_INPLACE_DATA);
          if (stepData != null) {
            stepData.clear();
          }
        });
      }

      @Override
      public void sessionPaused() {
        onAnyEvent();
      }

      @Override
      public void sessionResumed() {
        onAnyEvent();
      }

      @Override
      public void sessionStopped() {
        onAnyEvent();
      }

      @Override
      public void stackFrameChanged() {
        onAnyEvent();
      }

      @Override
      public void settingsChanged() {
        onAnyEvent();
      }
    });
 }

  private static <V extends XSmartStepIntoVariant> void showInfoHint(Editor editor, SmartStepData<V> data) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    int counter = propertiesComponent.getInt(COUNTER_PROPERTY, 0);
    if (counter < 3) {
      Hint hint = showHint(editor, XDebuggerBundle.message("message.smart.step"), data.myCurrentVariant);
      editor.putUserData(SMART_STEP_HINT_DATA, hint);
      propertiesComponent.setValue(COUNTER_PROPERTY, counter + 1, 0);
    }
  }

  private static <V extends XSmartStepIntoVariant> Hint showHint(Editor editor,
                                                                 @NlsContexts.HintText String message,
                                                                 SmartStepData<V>.VariantInfo myCurrentVariant) {
    LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
    JComponent component = HintManagerImpl.getExternalComponent(editor);
    Point convertedPoint = SwingUtilities.convertPoint(editor.getContentComponent(), myCurrentVariant.myStartPoint, component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, convertedPoint, HintManager.HIDE_BY_TEXT_CHANGE |
                                                                                   HintManager.HIDE_BY_SCROLLING,
                                                     0, false, HintManager.ABOVE);
    return hint;
  }

  private static boolean shouldShowElementDescription(Editor editor) {
    Hint hint = editor.getUserData(SMART_STEP_HINT_DATA);
    return hint == null || !hint.isVisible();
  }

  static final Key<SmartStepData> SMART_STEP_INPLACE_DATA = Key.create("SMART_STEP_INPLACE_DATA");
  static final Key<Hint> SMART_STEP_HINT_DATA = Key.create("SMART_STEP_HINT_DATA");

  static class SmartStepData<V extends XSmartStepIntoVariant> {
    enum Direction {UP, DOWN, LEFT, RIGHT}
    private final XSmartStepIntoHandler<V> myHandler;
    private final List<VariantInfo> myVariants;
    private final XDebugSession mySession;
    private final Editor myEditor;
    private VariantInfo myCurrentVariant;
    private final List<RangeHighlighter> myHighlighters = new ArrayList<>();
    private RangeHighlighter myActionHintSyntheticHighlighter;

    SmartStepData(final XSmartStepIntoHandler<V> handler, List<? extends V> variants, final XDebugSession session, Editor editor) {
      myHandler = handler;
      mySession = session;
      myEditor = editor;
      myVariants =
        StreamEx.of(variants)
          .map(VariantInfo::new)
          .sorted(Comparator.<VariantInfo>comparingInt(v -> v.myVariant.getHighlightRange().getStartOffset())
                    .thenComparingInt(v -> v.myVariant.getHighlightRange().getLength()))
          .toList();
    }

    final Comparator<VariantInfo> DISTANCE_TO_CURRENT_COMPARATOR =
      Comparator.comparingInt(a -> Math.abs(a.myStartPoint.x - myCurrentVariant.myStartPoint.x));

    private VariantInfo getPreviousVariant() {
      int currentIndex = myVariants.indexOf(myCurrentVariant);
      return myVariants.get(currentIndex > 0 ? currentIndex - 1 : myVariants.size() - 1);
    }

    private VariantInfo getNextVariant() {
      int currentIndex = myVariants.indexOf(myCurrentVariant);
      return myVariants.get(currentIndex < myVariants.size() - 1 ? currentIndex + 1 : 0);
    }

    void selectNext(Direction direction) {
      int currentLineY = myCurrentVariant.myStartPoint.y;
      VariantInfo next = switch (direction) {
        case LEFT -> getPreviousVariant();
        case RIGHT -> getNextVariant();
        case UP -> {
          int previousLineY = myVariants.stream().mapToInt(v -> v.myStartPoint.y).filter(v -> v < currentLineY).max().orElse(-1);
          yield myVariants.stream()
            .filter(v -> v.myStartPoint.y == previousLineY)
            .min(DISTANCE_TO_CURRENT_COMPARATOR)
            .orElseGet(this::getPreviousVariant);
        }
        case DOWN -> {
          int nextLineY = myVariants.stream().mapToInt(v -> v.myStartPoint.y).filter(v -> v > currentLineY).min().orElse(-1);
          yield myVariants.stream()
            .filter(v -> v.myStartPoint.y == nextLineY)
            .min(DISTANCE_TO_CURRENT_COMPARATOR)
            .orElseGet(this::getNextVariant);
        }
      };
      if (next != null) {
        select(next);
      }
    }

    void select(@NotNull VariantInfo variant) {
      setCurrentVariantHighlighterAttributes(DebuggerColors.SMART_STEP_INTO_TARGET);
      myCurrentVariant = variant;
      setCurrentVariantHighlighterAttributes(DebuggerColors.SMART_STEP_INTO_SELECTION);

      String description = variant.myVariant.getDescription();
      if (description != null && shouldShowElementDescription(myEditor)) {
        showHint(myEditor, description, variant);
      }
    }

    private void setCurrentVariantHighlighterAttributes(TextAttributesKey attributesKey) {
      int index = myVariants.indexOf(myCurrentVariant);
      if (index != -1) {
        myHighlighters.get(index).setTextAttributesKey(attributesKey);
      }
    }

    void stepInto(@NotNull VariantInfo variant) {
      clear();
      mySession.smartStepInto(myHandler, variant.myVariant);
    }

    void clear() {
      myEditor.putUserData(SMART_STEP_INPLACE_DATA, null);
      myEditor.putUserData(SMART_STEP_HINT_DATA, null);
      HighlightManagerImpl highlightManager = (HighlightManagerImpl)HighlightManager.getInstance(mySession.getProject());
      highlightManager.hideHighlights(myEditor, HighlightManager.HIDE_BY_ESCAPE | HighlightManager.HIDE_BY_TEXT_CHANGE);
      // since we don't use HighlightManagerImpl to mark the highlighting with the hide flags it can't be used to remove it as well
      // just remove it manually
      if (myActionHintSyntheticHighlighter != null) myEditor.getMarkupModel().removeHighlighter(myActionHintSyntheticHighlighter);
    }

    class VariantInfo {
      @NotNull final V myVariant;
      @NotNull final Point myStartPoint;

      VariantInfo(@NotNull V variant) {
        myVariant = variant;
        myStartPoint = myEditor.offsetToXY(variant.getHighlightRange().getStartOffset());
      }
    }
  }

  static abstract class SmartStepEditorActionHandler extends EditorActionHandler {
    protected final EditorActionHandler myOriginalHandler;

    SmartStepEditorActionHandler(EditorActionHandler originalHandler) {
      myOriginalHandler = originalHandler;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      SmartStepData stepData = editor.getUserData(SMART_STEP_INPLACE_DATA);
      if (stepData != null) {
        myPerform(editor, caret, dataContext, stepData);
      }
      else {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return editor.getUserData(SMART_STEP_INPLACE_DATA) != null || myOriginalHandler.isEnabled(editor, caret, dataContext);
    }

    protected abstract void myPerform(@NotNull Editor editor,
                                      @Nullable Caret caret,
                                      DataContext dataContext,
                                      SmartStepData stepData);
  }

  static final class UpHandler extends SmartStepEditorActionHandler {
    UpHandler(EditorActionHandler original) {
      super(original);
    }

    @Override
    protected void myPerform(@NotNull Editor editor,
                             @Nullable Caret caret,
                             DataContext dataContext,
                             SmartStepData stepData) {
      stepData.selectNext(SmartStepData.Direction.UP);
    }
  }

  static final class DownHandler extends SmartStepEditorActionHandler {
    DownHandler(EditorActionHandler original) {
      super(original);
    }

    @Override
    protected void myPerform(@NotNull Editor editor,
                             @Nullable Caret caret,
                             DataContext dataContext,
                             SmartStepData stepData) {
      stepData.selectNext(SmartStepData.Direction.DOWN);
    }
  }

  static final class LeftHandler extends SmartStepEditorActionHandler {
    LeftHandler(EditorActionHandler original) {
      super(original);
    }

    @Override
    protected void myPerform(@NotNull Editor editor,
                             @Nullable Caret caret,
                             DataContext dataContext,
                             SmartStepData stepData) {
      stepData.selectNext(SmartStepData.Direction.LEFT);
    }
  }

  static final class RightHandler extends SmartStepEditorActionHandler {
    RightHandler(EditorActionHandler original) {
      super(original);
    }

    @Override
    protected void myPerform(@NotNull Editor editor,
                             @Nullable Caret caret,
                             DataContext dataContext,
                             SmartStepData stepData) {
      stepData.selectNext(SmartStepData.Direction.RIGHT);
    }
  }

  static final class EscHandler extends SmartStepEditorActionHandler {
    EscHandler(EditorActionHandler original) {
      super(original);
    }

    @Override
    protected void myPerform(@NotNull Editor editor,
                             @Nullable Caret caret,
                             DataContext dataContext,
                             SmartStepData stepData) {
      editor.putUserData(SMART_STEP_INPLACE_DATA, null);
      if (myOriginalHandler.isEnabled(editor, caret, dataContext)) {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
    }
  }

  static final class EnterHandler extends SmartStepEditorActionHandler {
    EnterHandler(EditorActionHandler original) {
      super(original);
    }

    @Override
    protected void myPerform(@NotNull Editor editor,
                             @Nullable Caret caret,
                             DataContext dataContext,
                             SmartStepData stepData) {
      stepData.stepInto(stepData.myCurrentVariant);
    }
  }
}

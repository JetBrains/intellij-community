// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.CommonBundle;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.breakpoints.*;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointFileGroupingRule;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import com.intellij.xdebugger.ui.DebuggerColors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.jetbrains.concurrency.Promises.rejectedPromise;
import static org.jetbrains.concurrency.Promises.resolvedPromise;

public class XDebuggerUtilImpl extends XDebuggerUtil {
  private static final Ref<Boolean> SHOW_BREAKPOINT_AD = new Ref<>(true);

  public static final DataKey<Integer> LINE_NUMBER = DataKey.create("x.debugger.line.number");

  @Override
  public XLineBreakpointType<?>[] getLineBreakpointTypes() {
    return XBreakpointUtil.breakpointTypes().select(XLineBreakpointType.class).toArray(XLineBreakpointType<?>[]::new);
  }

  @Override
  public void toggleLineBreakpoint(@NotNull final Project project, @NotNull final VirtualFile file, final int line, boolean temporary) {
    toggleAndReturnLineBreakpoint(project, file, line, temporary);
  }

  @Nullable
  private XLineBreakpointType<?> getBreakpointTypeByPosition(@NotNull final Project project,
                                                            @NotNull final VirtualFile file,
                                                            final int line) {
    XLineBreakpointType<?> typeWinner = null;
    for (XLineBreakpointType<?> type : getLineBreakpointTypes()) {
      if (type.canPutAt(file, line, project) && (typeWinner == null || type.getPriority() > typeWinner.getPriority())) {
        typeWinner = type;
      }
    }
    return typeWinner;
  }

  @NotNull
  public Promise<@Nullable XLineBreakpoint> toggleAndReturnLineBreakpoint(@NotNull final Project project,
                                                                @NotNull final VirtualFile file,
                                                                final int line,
                                                                boolean temporary) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final XLineBreakpointType<?> typeWinner = getBreakpointTypeByPosition(project, file, line);
    if (typeWinner != null) {
      return toggleAndReturnLineBreakpoint(project, typeWinner, file, line, temporary);
    }
    return rejectedPromise(new RuntimeException("Cannot find appropriate breakpoint type"));
  }

  @Override
  public boolean canPutBreakpointAt(@NotNull Project project, @NotNull VirtualFile file, int line) {
    return ContainerUtil.exists(getLineBreakpointTypes(), type -> type.canPutAt(file, line, project));
  }

  @Override
  public <P extends XBreakpointProperties> void toggleLineBreakpoint(@NotNull final Project project,
                                                                     @NotNull final XLineBreakpointType<P> type,
                                                                     @NotNull final VirtualFile file,
                                                                     final int line,
                                                                     final boolean temporary) {
    toggleAndReturnLineBreakpoint(project, type, file, line, temporary);
  }

  @NotNull
  public <P extends XBreakpointProperties> Promise<@Nullable XLineBreakpoint> toggleAndReturnLineBreakpoint(@NotNull final Project project,
                                                                                                  @NotNull final XLineBreakpointType<P> type,
                                                                                                  @NotNull final VirtualFile file,
                                                                                                  final int line,
                                                                                                  final boolean temporary) {
    XSourcePositionImpl position = XSourcePositionImpl.create(file, line);
    return toggleAndReturnLineBreakpoint(project, Collections.singletonList(type), position, temporary, null, true);
  }

  /**
   * Get non-empty list of variants assuming that given list of types is non-empty too.
   */
  public static Promise<List<? extends XLineBreakpointType.XLineBreakpointVariant>>
  getLineBreakpointVariants(@NotNull final Project project,
                            @NotNull List<? extends XLineBreakpointType> types,
                            @NotNull final XSourcePosition position) {
    if (types.isEmpty()) {
      return rejectedPromise("non-empty types are expected");
    }

    boolean multipleTypes = types.size() > 1;
    List<Promise<List<? extends XLineBreakpointType.XLineBreakpointVariant>>> promises = new SmartList<>();
    for (XLineBreakpointType type : types) {
      promises.add(type.computeVariantsAsync(project, position).then(o -> {
        if (((List<?>)o).isEmpty() && multipleTypes) {
          // We have multiple types, but no non-default variants for this type. So we just create one.
          return Collections.singletonList(createDefaultBreakpointVariant(position, type));
        }
        else {
          return o;
        }
      }));
    }
    return Promises.collectResults(promises).then(v -> {
      var variants = StreamEx.of(v).toFlatList(l -> l);
      if (variants.isEmpty()) {
        assert !multipleTypes;
        return Collections.singletonList(createDefaultBreakpointVariant(position, types.get(0)));
      } else {
        return variants;
      }
    });
  }

  @NotNull
  private static XLineBreakpointType.XLineBreakpointAllVariant createDefaultBreakpointVariant(@NotNull XSourcePosition position, XLineBreakpointType type) {
    return type.new XLineBreakpointAllVariant(position) {
      @NotNull
      @Override
      public String getText() {
        return StringUtil.notNullize(StringUtil.unpluralize(type.getTitle()), type.getTitle());
      }

      @Override
      public @NotNull Icon getIcon() {
        return type.getEnabledIcon();
      }
    };
  }

  private static int getIndexOfBestMatchingInlineVariant(int caretOffset, List<? extends XLineBreakpointType.XLineBreakpointVariant> variants) {
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

  private static <T> @Nullable T getBestMatchingBreakpoint(int caretOffset, Iterator<@NotNull T> breakpoints, Function<T, @Nullable TextRange> rangeProvider) {
    // Best matching = minimal by range of all breakpoints containing caret offset in their range.
    T bestBreakpoint = null;
    int bestRangeLength = Integer.MAX_VALUE;
    while (breakpoints.hasNext()) {
      var b = breakpoints.next();
      TextRange range = rangeProvider.apply(b);
      int rangeLength = range != null ? range.getLength() : Integer.MAX_VALUE;
      // note that range = null means "whole line"
      if (range == null || range.contains(caretOffset)) {
        if (bestBreakpoint == null || rangeLength < bestRangeLength) {
          bestBreakpoint = b;
          bestRangeLength = rangeLength;
        }
      }
    }
    return bestBreakpoint;
  }

  @NotNull
  public static Promise<@Nullable XLineBreakpoint> toggleAndReturnLineBreakpoint(@NotNull final Project project,
                                                                       @NotNull List<? extends XLineBreakpointType> types,
                                                                       @NotNull final XSourcePosition position,
                                                                       final boolean temporary,
                                                                       @Nullable final Editor editor,
                                                                       boolean canRemove) {
    final VirtualFile file = position.getFile();
    final int line = position.getLine();
    // FIXME[inline-bp]: it seems to work as good as editor.getCaretModel().getOffset() and in case of folding even better?
    //                   think about it a bit more
    final int caretOffset = position.getOffset();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();

    Promise<List<? extends XLineBreakpointType.XLineBreakpointVariant>> variantsAsync = getLineBreakpointVariants(project, types, position);
    if (XLineBreakpointManager.shouldShowBreakpointsInline()) {
      return variantsAsync.then(variants -> {

        var breakpointOrVariant = getBestMatchingBreakpoint(caretOffset,
                                                            Stream.concat(
                                                              types.stream().flatMap(t -> breakpointManager.findBreakpointsAtLine(t, file, line).stream()),
                                                              variants.stream()).iterator(),
                                                            o ->
                                                              // FIXME[inline-bp]: introduce HasHighlightRange interface?
                                                              o instanceof XLineBreakpoint b
                                                              ? b.getType().getHighlightRange(b)
                                                              : ((XLineBreakpointType.XLineBreakpointVariant)o).getHighlightRange());

        if (breakpointOrVariant instanceof XLineBreakpoint existingBreakpoint) {
          if (!temporary && canRemove) {
            removeBreakpointWithConfirmation(project, existingBreakpoint);
          }
          return null;
        }

        assert !variants.isEmpty();
        XLineBreakpointType.XLineBreakpointVariant variant;
        if (variants.size() > 1) {
          assert editor != null; // FIXME: it's absolutely not true, but I want to look at this use cases
          variant = breakpointOrVariant instanceof XLineBreakpointType.XLineBreakpointVariant v ? v : variants.get(0);
        } else {
          variant = variants.get(0);
        }
        return insertBreakpoint(variant.createProperties(), breakpointManager, file, line, variant.getType(), temporary);
      });
      // FIXME[inline-bp]: review code below, I was able to loose something non-trivial there
    }

    for (XLineBreakpointType type : types) {
      XLineBreakpoint breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
      if (breakpoint != null) {
        if (!temporary && canRemove) {
          removeBreakpointWithConfirmation(project, breakpoint);
        }
        return resolvedPromise();
      }
    }

    return variantsAsync.thenAsync(variants -> {
      assert !variants.isEmpty();
      final AsyncPromise<XLineBreakpoint> res = new AsyncPromise<>();
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
        for (XLineBreakpointType<?> type : types) {
          if (breakpointManager.findBreakpointAtLine(type, file, line) != null) {
            return;
          }
        }
        RelativePoint relativePoint = editor != null ? DebuggerUIUtil.getPositionForPopup(editor, line) : null;
        if (variants.size() > 1 && relativePoint != null) {
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
              if (value instanceof XLineBreakpointType.XLineBreakpointVariant) {
                TextRange range = ((XLineBreakpointType.XLineBreakpointVariant)value).getHighlightRange();
                TextRange lineRange = DocumentUtil.getLineTextRange(editor.getDocument(), line);
                if (range == null) {
                  range = lineRange;
                }
                if (!range.isEmpty() && range.intersects(lineRange)) {
                  myHighlighter = editor.getMarkupModel().addRangeHighlighter(
                    DebuggerColors.BREAKPOINT_ATTRIBUTES, range.getStartOffset(), range.getEndOffset(), DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER,
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

          final int defaultIndex = getIndexOfBestMatchingInlineVariant(caretOffset, variants);

          final MySelectionListener selectionListener = new MySelectionListener();
          BaseListPopupStep<XLineBreakpointType.XLineBreakpointVariant> step =
            new BaseListPopupStep<>(XDebuggerBundle.message("popup.title.set.breakpoint"), variants) {
              @NotNull
              @Override
              public String getTextFor(XLineBreakpointType.XLineBreakpointVariant value) {
                return value.getText();
              }

              @Override
              public Icon getIconFor(XLineBreakpointType.XLineBreakpointVariant value) {
                return value.getIcon();
              }

              @Override
              public void canceled() {
                selectionListener.clearHighlighter();
                res.cancel();
              }

              @Override
              public PopupStep onChosen(final XLineBreakpointType.XLineBreakpointVariant selectedValue, boolean finalChoice) {
                selectionListener.clearHighlighter();
                insertBreakpoint(selectedValue.createProperties(), res, breakpointManager, file, line, selectedValue.getType(),
                                 temporary);
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
        else {
          XLineBreakpointType.XLineBreakpointVariant variant = variants.get(0);
          insertBreakpoint(variant.createProperties(), res, breakpointManager, file, line, variant.getType(), temporary);
        }
      });
      return res;
    });
  }

  private static <P extends XBreakpointProperties> void insertBreakpoint(P properties,
                                                                         AsyncPromise<? super XLineBreakpoint> res,
                                                                         XBreakpointManager breakpointManager,
                                                                         VirtualFile file,
                                                                         int line,
                                                                         XLineBreakpointType<P> type,
                                                                         Boolean temporary) {
    res.setResult(insertBreakpoint(properties, breakpointManager, file, line, type, temporary));
  }

  private static <P extends XBreakpointProperties> XLineBreakpoint insertBreakpoint(P properties,
                                                                         XBreakpointManager breakpointManager,
                                                                         VirtualFile file,
                                                                         int line,
                                                                         XLineBreakpointType<P> type,
                                                                         Boolean temporary) {
    return WriteAction.compute(() -> breakpointManager.addLineBreakpoint(type, file.getUrl(), line, properties, temporary));
  }

  public static boolean removeBreakpointWithConfirmation(final XBreakpointBase<?, ?, ?> breakpoint) {
    return removeBreakpointWithConfirmation(breakpoint.getProject(), breakpoint);
  }

  public static void reshowInlayRunToCursor(@NotNull AnActionEvent e) {
    if (!(e.getInputEvent() instanceof MouseEvent)) {
      return;
    }

    Project project = e.getProject();
    if (project == null) {
      return;
    }

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if(editor == null) {
      return;
    }

    if (XDebuggerManager.getInstance(project) instanceof XDebuggerManagerImpl debuggerManagerImpl) {
      debuggerManagerImpl.reshowInlayToolbar(editor);
    }
  }

  /**
   * Remove breakpoint. Show confirmation dialog if breakpoint has non-empty condition or log expression.
   * Returns whether breakpoint was really deleted.
   */
  public static boolean removeBreakpointWithConfirmation(final Project project, final XBreakpoint<?> breakpoint) {
    if ((!isEmptyExpression(breakpoint.getConditionExpression()) || !isEmptyExpression(breakpoint.getLogExpressionObject())) &&
        !ApplicationManager.getApplication().isHeadlessEnvironment() &&
        !ApplicationManager.getApplication().isUnitTestMode() &&
        XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isConfirmBreakpointRemoval()) {
      StringBuilder message = new StringBuilder("<html>").append(XDebuggerBundle.message("message.confirm.breakpoint.removal.message"));
      if (!isEmptyExpression(breakpoint.getConditionExpression())) {
        message.append("<br>")
          .append(XDebuggerBundle.message("message.confirm.breakpoint.removal.message.condition"))
          .append("<br><pre>")
          .append(StringUtil.escapeXmlEntities(breakpoint.getConditionExpression().getExpression()))
          .append("</pre>");
      }
      if (!isEmptyExpression(breakpoint.getLogExpressionObject())) {
        message.append("<br>")
          .append(XDebuggerBundle.message("message.confirm.breakpoint.removal.message.log"))
          .append("<br><pre>")
          .append(StringUtil.escapeXmlEntities(breakpoint.getLogExpressionObject().getExpression()))
          .append("</pre>");
      }
      //noinspection HardCodedStringLiteral
      if (Messages.showOkCancelDialog(message.toString(),
                                      XDebuggerBundle.message("message.confirm.breakpoint.removal.title"),
                                      CommonBundle.message("button.remove"),
                                      Messages.getCancelButton(),
                                      Messages.getQuestionIcon(),
                                      new DialogWrapper.DoNotAskOption.Adapter() {
                                        @Override
                                        public void rememberChoice(boolean isSelected, int exitCode) {
                                          if (isSelected) {
                                            XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings()
                                                                       .setConfirmBreakpointRemoval(false);
                                          }
                                        }
                                      }) != Messages.OK) {
        return false;
      }
    }
    ((XBreakpointManagerImpl)XDebuggerManager.getInstance(project).getBreakpointManager())
      .rememberRemovedBreakpoint((XBreakpointBase)breakpoint);
    getInstance().removeBreakpoint(project, breakpoint);
    return true;
  }

  @Override
  public void removeBreakpoint(final Project project, final XBreakpoint<?> breakpoint) {
    WriteAction.run(() -> XDebuggerManager.getInstance(project).getBreakpointManager().removeBreakpoint(breakpoint));
  }

  @Override
  public <T extends XBreakpointType> T findBreakpointType(@NotNull Class<T> typeClass) {
    return XBreakpointType.EXTENSION_POINT_NAME.findExtension(typeClass);
  }

  @Override
  public <T extends XDebuggerSettings<?>> T getDebuggerSettings(Class<T> aClass) {
    return XDebuggerSettingManagerImpl.getInstanceImpl().getSettings(aClass);
  }

  @Override
  public XValueContainer getValueContainer(DataContext dataContext) {
    return XDebuggerTreeActionBase.getSelectedValue(dataContext);
  }

  @Override
  @Nullable
  public XSourcePosition createPosition(@Nullable VirtualFile file, int line) {
    return file == null ? null : XSourcePositionImpl.create(file, line);
  }

  @Override
  @Nullable
  public XSourcePosition createPosition(@Nullable VirtualFile file, final int line, final int column) {
    return file == null ? null : XSourcePositionImpl.create(file, line, column);
  }

  @Override
  @Nullable
  public XSourcePosition createPositionByOffset(final VirtualFile file, final int offset) {
    return XSourcePositionImpl.createByOffset(file, offset);
  }

  @Override
  @Nullable
  public XSourcePosition createPositionByElement(PsiElement element) {
    return XSourcePositionImpl.createByElement(element);
  }

  @Override
  public <B extends XLineBreakpoint<?>> XBreakpointGroupingRule<B, ?> getGroupingByFileRule() {
    return new XBreakpointFileGroupingRule<>();
  }

  @Nullable
  public static XSourcePosition getCaretPosition(@NotNull Project project, DataContext context) {
    Editor editor = getEditor(project, context);
    if (editor == null) return null;

    Integer lineNumber = LINE_NUMBER.getData(context);
    if (lineNumber != null) {
      return XSourcePositionImpl.create(editor.getVirtualFile(), lineNumber);
    }

    final Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return XSourcePositionImpl.createByOffset(file, offset);
  }

  @Nullable
  public static Editor getEditor(@NotNull Project project, DataContext context) {
    Editor editor = CommonDataKeys.EDITOR.getData(context);
    if (editor == null) {
      @Nullable FileEditor fileEditor = context.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR);
      return fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
    }
    return editor;
  }

  @Override
  public <B extends XBreakpoint<?>> Comparator<B> getDefaultBreakpointComparator(final XBreakpointType<B, ?> type) {
    return Comparator.comparing(type::getDisplayText);
  }

  @Override
  public void iterateLine(@NotNull Project project, @NotNull Document document, int line, @NotNull Processor<? super PsiElement> processor) {
    int lineStart;
    int lineEnd;
    try {
      lineStart = document.getLineStartOffset(line);
      lineEnd = document.getLineEndOffset(line);
    }
    catch (IndexOutOfBoundsException ignored) {
      return;
    }

    iterateOffsetRange(project, document, lineStart, lineEnd, processor);
  }

  public void iterateOffsetRange(@NotNull Project project, @NotNull Document document, int startOffset, int endOffset,
                                 @NotNull Processor<? super PsiElement> processor) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) {
      return;
    }
    PsiElement element;
    int offset = startOffset;
    while (offset < endOffset) {
      element = file.findElementAt(offset);
      if (element != null && element.getTextLength() > 0) {
        if (!processor.process(element)) {
          return;
        }
        else {
          offset = element.getTextRange().getEndOffset();
        }
      }
      else {
        offset++;
      }
    }
  }

  @Override
  public <B extends XLineBreakpoint<?>> List<XBreakpointGroupingRule<B, ?>> getGroupingByFileRuleAsList() {
    return Collections.singletonList(getGroupingByFileRule());
  }

  @Override
  @Nullable
  public PsiElement findContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project, boolean checkXml) {
    if (!virtualFile.isValid()) {
      return null;
    }

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    PsiFile file = document == null ? null : PsiManager.getInstance(project).findFile(virtualFile);
    if (file == null) {
      return null;
    }

    if (offset < 0) {
      offset = 0;
    }
    if (offset > document.getTextLength()) {
      offset = document.getTextLength();
    }
    int startOffset = offset;

    int lineEndOffset = document.getLineEndOffset(document.getLineNumber(offset));
    PsiElement result = null;
    do {
      PsiElement element = file.findElementAt(offset);
      if (!(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)) {
        result = element;
        break;
      }

      offset = element.getTextRange().getEndOffset() + 1;
    }
    while (offset < lineEndOffset);

    if (result == null) {
      result = file.findElementAt(startOffset);
    }

    if (checkXml && result != null && StdFileTypes.XML.getLanguage().equals(result.getLanguage())) {
      PsiLanguageInjectionHost parent = PsiTreeUtil.getParentOfType(result, PsiLanguageInjectionHost.class);
      if (parent != null) {
        result = InjectedLanguageUtil.findElementInInjected(parent, offset);
      }
    }
    return result;
  }

  @Override
  public void disableValueLookup(@NotNull Editor editor) {
    ValueLookupManager.DISABLE_VALUE_LOOKUP.set(editor, Boolean.TRUE);
  }


  @Override
  @Nullable
  public Editor openTextEditor(@NotNull OpenFileDescriptor descriptor) {
    return createEditor(descriptor);
  }

  public static Editor createEditor(@NotNull OpenFileDescriptor descriptor) {
    if (descriptor.canNavigate()) {
      FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(descriptor.getProject());
      boolean isEditorAreaFocused = IJSwingUtilities.hasFocus(fileEditorManager.getComponent());
      return fileEditorManager.openTextEditor(descriptor, isEditorAreaFocused);
    }
    return null;
  }

  /**
   * The returned Navigatable overrides requesting focus based on whether the editor area is focused or not.
   */
  public static @NotNull Navigatable wrapKeepEditorAreaFocusNavigatable(@NotNull Project project, @NotNull Navigatable navigatable) {
    return new Navigatable() {
      @Override
      public void navigate(boolean requestFocus) {
        FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
        boolean isEditorAreaFocused = IJSwingUtilities.hasFocus(fileEditorManager.getComponent());
        navigatable.navigate(requestFocus && isEditorAreaFocused);
      }

      @Override
      public boolean canNavigate() { return navigatable.canNavigate(); }

      @Override
      public boolean canNavigateToSource() { return navigatable.canNavigateToSource(); }
    };
  }

  public static void rebuildAllSessionsViews(@Nullable Project project) {
    if (project == null) return;
    Arrays.stream(XDebuggerManager.getInstance(project).getDebugSessions())
      .filter(XDebugSession::isSuspended)
      .forEach(XDebugSession::rebuildViews);
  }

  public static void rebuildTreeAndViews(XDebuggerTree tree) {
    if (tree.isDetached()) {
      tree.rebuildAndRestore(XDebuggerTreeState.saveState(tree));
    }
    rebuildAllSessionsViews(tree.getProject());
  }

  @NotNull
  @Override
  public XExpression createExpression(@NotNull String text, Language language, String custom, @NotNull EvaluationMode mode) {
    return new XExpressionImpl(text, language, custom, mode);
  }

  public static boolean isEmptyExpression(@Nullable XExpression expression) {
    return expression == null || StringUtil.isEmptyOrSpaces(expression.getExpression());
  }

  @Override
  public void logStack(@NotNull XSuspendContext suspendContext, @NotNull XDebugSession session) {
    XExecutionStack activeExecutionStack = suspendContext.getActiveExecutionStack();
    if (activeExecutionStack != null) {
      activeExecutionStack.computeStackFrames(0, new XStackFrameContainerEx() {
        final List<XStackFrame> myFrames = new ArrayList<>();

        @Override
        public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, boolean last) {
          myFrames.addAll(stackFrames);
          if (last) {
            print(null);
          }
        }

        @Override
        public void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, @Nullable XStackFrame toSelect, boolean last) {
          addStackFrames(stackFrames, last);
        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          print(errorMessage);
        }

        void print(@Nullable String errorMessage) {
          ConsoleView view = session.getConsoleView();
          Project project = session.getProject();
          DebuggerUIUtil.invokeLater(() -> view.print("Stack: ", ConsoleViewContentType.SYSTEM_OUTPUT));
          myFrames.forEach(f -> {
            SimpleColoredText text = new SimpleColoredText();
            ReadAction.run(() -> f.customizePresentation(text));
            XSourcePosition position = f.getSourcePosition();
            Navigatable navigatable = position != null ? position.createNavigatable(project) : null;
            DebuggerUIUtil.invokeLater(() -> {
              view.print("\n\t", ConsoleViewContentType.SYSTEM_OUTPUT);
              view.printHyperlink(text.toString(), p -> {
                if (navigatable != null) {
                  navigatable.navigate(true);
                }
              });
            });
          });
          DebuggerUIUtil.invokeLater(() -> {
            if (errorMessage != null) {
              view.print("\n\t" + errorMessage, ConsoleViewContentType.SYSTEM_OUTPUT);
            }
            view.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT);
          });
        }
      });
    }
  }

  public static Icon getVerifiedIcon(@NotNull XBreakpoint breakpoint) {
    return breakpoint.getSuspendPolicy() == SuspendPolicy.NONE
           ? AllIcons.Debugger.Db_verified_no_suspend_breakpoint
           : AllIcons.Debugger.Db_verified_breakpoint;
  }

  public static Navigatable createNavigatable(Project project, XSourcePosition position) {
    return new XSourcePositionNavigatable(project, position);
  }

  private static class XSourcePositionNavigatable implements Navigatable {
    private final Project myProject;
    private final XSourcePosition myPosition;

    private XSourcePositionNavigatable(Project project, XSourcePosition position) {
      myProject = project;
      myPosition = position;
    }

    @Override
    public void navigate(boolean requestFocus) {
      createOpenFileDescriptor(myProject, myPosition).navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return myPosition.getFile().isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }
  }

  @NotNull
  public static OpenFileDescriptor createOpenFileDescriptor(@NotNull Project project, @NotNull XSourcePosition position) {
    return position.getOffset() != -1
           ? new OpenFileDescriptor(project, position.getFile(), position.getOffset())
           : new OpenFileDescriptor(project, position.getFile(), position.getLine(), 0);
  }
}

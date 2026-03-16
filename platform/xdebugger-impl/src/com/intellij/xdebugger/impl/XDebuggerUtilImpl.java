// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.debugger.impl.shared.DebuggerAsyncActionUtilsKt;
import com.intellij.platform.debugger.impl.shared.XDebuggerUtilImplShared;
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy;
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointManagerProxy;
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointInstallUtils;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointFileGroupingRule;
import com.intellij.xdebugger.impl.evaluate.ValueLookupManagerController;
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx;
import com.intellij.xdebugger.impl.proxy.MonolithLineBreakpointProxy;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import kotlin.Unit;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.Icon;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.concurrency.Promises.asPromise;
import static org.jetbrains.concurrency.Promises.rejectedPromise;

@ApiStatus.Internal
public class XDebuggerUtilImpl extends XDebuggerUtil {
  private static final Logger LOG = Logger.getInstance(XDebuggerUtilImpl.class);

  @Override
  public XLineBreakpointType<?>[] getLineBreakpointTypes() {
    return XBreakpointUtil.breakpointTypes().select(XLineBreakpointType.class).toArray(XLineBreakpointType<?>[]::new);
  }

  @Override
  public void toggleLineBreakpoint(final @NotNull Project project, final @NotNull VirtualFile file, final int line, boolean temporary) {
    toggleAndReturnLineBreakpoint(project, file, line, temporary);
  }

  private @Nullable XLineBreakpointType<?> getBreakpointTypeByPosition(final @NotNull Project project,
                                                                       final @NotNull VirtualFile file,
                                                                       final int line) {
    XLineBreakpointType<?> typeWinner = null;
    for (XLineBreakpointType<?> type : getLineBreakpointTypes()) {
      if (type.canPutAt(file, line, project) && (typeWinner == null || type.getPriority() > typeWinner.getPriority())) {
        typeWinner = type;
      }
    }
    return typeWinner;
  }

  public @NotNull Promise<@Nullable XLineBreakpoint> toggleAndReturnLineBreakpoint(final @NotNull Project project,
                                                                                   final @NotNull VirtualFile file,
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
  public <P extends XBreakpointProperties> void toggleLineBreakpoint(final @NotNull Project project,
                                                                     final @NotNull XLineBreakpointType<P> type,
                                                                     final @NotNull VirtualFile file,
                                                                     final int line,
                                                                     final boolean temporary) {
    toggleAndReturnLineBreakpoint(project, type, file, line, temporary);
  }

  public @NotNull <P extends XBreakpointProperties> Promise<@Nullable XLineBreakpoint> toggleAndReturnLineBreakpoint(final @NotNull Project project,
                                                                                                                     final @NotNull XLineBreakpointType<P> type,
                                                                                                                     final @NotNull VirtualFile file,
                                                                                                                     final int line,
                                                                                                                     final boolean temporary) {
    XSourcePositionImpl position = XSourcePositionImpl.create(file, line);
    return toggleAndReturnLineBreakpoint(project, Collections.singletonList(type), position, false, temporary, null, true);
  }

  /**
   * Get non-empty list of variants assuming that given list of types is non-empty too.
   */
  public static List<? extends XLineBreakpointType.XLineBreakpointVariant>
  getLineBreakpointVariantsSync(final @NotNull Project project,
                                @NotNull List<? extends XLineBreakpointType> types,
                                final @NotNull XSourcePosition position) {
    if (types.isEmpty()) {
      throw new IllegalArgumentException("non-empty types are expected");
    }

    boolean multipleTypes = types.size() > 1;
    List<XLineBreakpointType.XLineBreakpointVariant> allVariants = new SmartList<>();
    for (XLineBreakpointType type : types) {
      var variants = type.computeVariants(project, position);
      if (variants.isEmpty() && multipleTypes) {
        // We have multiple types, but no non-default variants for this type. So we just create one.
        allVariants.add(createDefaultBreakpointVariant(position, type));
      }
      else {
        allVariants.addAll(variants);
      }
    }

    if (allVariants.isEmpty()) {
      assert !multipleTypes;
      return Collections.singletonList(createDefaultBreakpointVariant(position, types.get(0)));
    } else {
      return allVariants;
    }
  }

  /**
   * Get non-empty list of variants assuming that given list of types is non-empty too.
   */
  public static Promise<List<? extends XLineBreakpointType.XLineBreakpointVariant>>
  getLineBreakpointVariants(final @NotNull Project project,
                            @NotNull List<? extends XLineBreakpointType> types,
                            final @NotNull XSourcePosition position) {
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

  private static @NotNull XLineBreakpointType.XLineBreakpointAllVariant createDefaultBreakpointVariant(@NotNull XSourcePosition position, XLineBreakpointType type) {
    return type.new XLineBreakpointAllVariant(position) {
      @Override
      public @NotNull String getText() {
        return StringUtil.notNullize(StringUtil.unpluralize(type.getTitle()), type.getTitle());
      }

      @Override
      public @NotNull Icon getIcon() {
        return type.getEnabledIcon();
      }

      @Override
      public boolean isMultiVariant() {
        // TODO[inline-bp]: unfortunatelly it's wrong for default line variant, which is currently "all" by default,
        //                  see IDEA-336373.
        return false;
      }
    };
  }

  /**
   * @deprecated use {@link #toggleAndReturnLineBreakpoint(Project, List, XSourcePosition, boolean, boolean, Editor, boolean)}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull Promise<@Nullable XLineBreakpoint> toggleAndReturnLineBreakpoint(final @NotNull Project project,
                                                                                 @NotNull List<? extends XLineBreakpointType> types,
                                                                                 final @NotNull XSourcePosition position,
                                                                                 final boolean temporary,
                                                                                 final @Nullable Editor editor,
                                                                                 boolean canRemove) {
    return toggleAndReturnLineBreakpoint(project, types, position, true, temporary, editor, canRemove);
  }

  /**
   * Use {@link XBreakpointInstallUtils#toggleAndReturnLineBreakpointProxy} instead.
   */
  @ApiStatus.Obsolete
  public static @NotNull Promise<@Nullable XLineBreakpoint> toggleAndReturnLineBreakpoint(
    final @NotNull Project project,
    @NotNull List<? extends XLineBreakpointType> types,
    final @NotNull XSourcePosition position,
    boolean selectVariantByPositionColumn,
    final boolean temporary,
    final @Nullable Editor editor,
    boolean canRemove
  ) {
    var proxyTypes = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).getLineBreakpointTypes().stream()
      .filter(type -> ContainerUtil.exists(types, t -> t.getId().equals(type.getId())))
      .toList();
    var future = XBreakpointInstallUtils.toggleAndReturnLineBreakpointProxy(
      project, proxyTypes, position, selectVariantByPositionColumn,
      temporary, editor, canRemove, false, null);
    return asPromise(future).then(b -> {
      if (b == null) return null;
      XBreakpoint<?> monolithBreakpoint = XDebuggerEntityConverter.getBreakpoint(b.getId());
      if (monolithBreakpoint instanceof XLineBreakpoint<?> lineBreakpoint) {
        return lineBreakpoint;
      }
      return null;
    });
  }

  public static <P extends XBreakpointProperties> XLineBreakpoint<P> addLineBreakpoint(XBreakpointManager breakpointManager,
                                                                                       XLineBreakpointType<P>.XLineBreakpointVariant variant,
                                                                                       VirtualFile file,
                                                                                       int line) {
    return addLineBreakpoint(breakpointManager, variant, file, line, false);
  }

  public static <P extends XBreakpointProperties> XLineBreakpoint<P> addLineBreakpoint(XBreakpointManager breakpointManager,
                                                                                       XLineBreakpointType<P>.XLineBreakpointVariant variant,
                                                                                       VirtualFile file,
                                                                                       int line,
                                                                                       Boolean temporary) {
    var properties = variant.createProperties();
    var type = variant.getType();
    var breakpoint = addLineBreakpoint(breakpointManager, type, properties, file, line, temporary);
    if (!type.variantAndBreakpointMatch(breakpoint, variant)) {
      LOG.error("breakpoint doesn't match source variant, " + type + ", " + variant.getClass());
    }
    return breakpoint;
  }

  private static <P extends XBreakpointProperties> XLineBreakpoint<P> addLineBreakpoint(XBreakpointManager breakpointManager,
                                                                                        XLineBreakpointType<P> type,
                                                                                        P properties,
                                                                                        VirtualFile file,
                                                                                        int line,
                                                                                        Boolean temporary) {
    return breakpointManager.addLineBreakpoint(type, file.getUrl(), line, properties, temporary);
  }

  @ApiStatus.Internal
  public static Collection<? extends XLineBreakpointImpl<?>> getDocumentBreakpoints(Document document, XLineBreakpointManagerProxy managerProxy) {
    return StreamEx.of(managerProxy.getDocumentBreakpointProxies(document))
      .select(MonolithLineBreakpointProxy.class)
      .map(MonolithLineBreakpointProxy::getBreakpoint)
      .toList();
  }

  /**
   * @see DebuggerAsyncActionUtilsKt#performDebuggerActionAsync
   */
  public static void performDebuggerAction(@NotNull AnActionEvent e, @NotNull Runnable action) {
    DebuggerAsyncActionUtilsKt.performDebuggerAction(e.getProject(), e.getDataContext(), () -> {
      action.run();
      return Unit.INSTANCE;
    });
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
    if (editor == null) {
      return;
    }

    reshowInlayRunToCursor(project, editor);
  }

  @ApiStatus.Internal
  public static void reshowInlayRunToCursor(Project project, Editor editor) {
    if (XDebuggerManager.getInstance(project) instanceof XDebuggerManagerImpl debuggerManagerImpl) {
      debuggerManagerImpl.reshowInlayToolbar(editor);
    }
  }

  @Override
  public void removeBreakpoint(Project project, XBreakpoint<?> breakpoint) {
    XDebuggerManager.getInstance(project).getBreakpointManager().removeBreakpoint(breakpoint);
  }

  public static void removeAllBreakpoints(@NotNull Project project) {
    ((XBreakpointManagerImpl)XDebuggerManager.getInstance(project).getBreakpointManager()).removeAllBreakpoints();
  }

  @Override
  public <T extends XBreakpointType> T findBreakpointType(@NotNull Class<T> typeClass) {
    return XBreakpointType.EXTENSION_POINT_NAME.findExtension(typeClass);
  }

  @Override
  public <T extends XDebuggerSettings<?>> T getDebuggerSettings(Class<T> aClass) {
    return XDebuggerSettingManagerImpl.getInstanceImpl().getSettings(aClass);
  }

  /**
   * @deprecated Use {@link XDebuggerTreeActionBase#getSelectedValue(DataContext)} instead.
   */
  @Deprecated
  @Override
  public XValueContainer getValueContainer(DataContext dataContext) {
    return XDebuggerTreeActionBase.getSelectedValue(dataContext);
  }

  @Override
  public @Nullable XSourcePosition createPosition(@Nullable VirtualFile file, int line) {
    return file == null ? null : XSourcePositionImpl.create(file, line);
  }

  @Override
  public @Nullable XSourcePosition createPosition(@Nullable VirtualFile file, final int line, final int column) {
    return file == null ? null : XSourcePositionImpl.create(file, line, column);
  }

  @Override
  public @Nullable XSourcePosition createPositionByOffset(final VirtualFile file, final int offset) {
    return XSourcePositionImpl.createByOffset(file, offset);
  }

  @Override
  public @Nullable XSourcePosition createPositionByElement(PsiElement element) {
    return XSourcePositionImpl.createByElement(element);
  }

  @Override
  public <B extends XLineBreakpoint<?>> XBreakpointGroupingRule<B, ?> getGroupingByFileRule() {
    return new XBreakpointFileGroupingRule<>();
  }

  /**
   * @deprecated Use {@link DebuggerUIUtil#getCaretPosition(DataContext)} instead.
   */
  @Deprecated
  public static @Nullable XSourcePosition getCaretPosition(@NotNull Project project, DataContext context) {
    return DebuggerUIUtil.getCaretPosition(context);
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
  public @Nullable PsiElement findContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project, boolean checkXml) {
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
    ValueLookupManagerController.DISABLE_VALUE_LOOKUP.set(editor, Boolean.TRUE);
  }


  @Override
  public @Nullable Editor openTextEditor(@NotNull OpenFileDescriptor descriptor) {
    return createEditor(descriptor);
  }

  public static Editor createEditor(@NotNull OpenFileDescriptor descriptor) {
    if (descriptor.canNavigate()) {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(descriptor.getProject());
      boolean isEditorAreaFocused = fileEditorManager.getFocusedEditor() != null;
      return fileEditorManager.openTextEditor(descriptor, isEditorAreaFocused);
    }
    return null;
  }

  public static void rebuildAllSessionsViews(@Nullable Project project) {
    DebuggerUIUtil.rebuildAllSessionsViews(project);
  }

  public static void rebuildTreeAndViews(XDebuggerTree tree) {
    DebuggerUIUtil.rebuildTreeAndViews(tree);
  }

  @Override
  public @NotNull XExpression createExpression(@NotNull String text, Language language, String custom, @NotNull EvaluationMode mode) {
    return new XExpressionImpl(text, language, custom, mode);
  }

  /**
   * @deprecated Use {@link DebuggerUIUtil#isEmptyExpression(XExpression)} instead.
   */
  @Deprecated
  public static boolean isEmptyExpression(@Nullable XExpression expression) {
    return DebuggerUIUtil.isEmptyExpression(expression);
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
            ReadAction.run(() -> f.customizeTextPresentation(text));
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
    return XDebuggerUtilImplShared.createNavigatable(project, position);
  }

  public static @NotNull OpenFileDescriptor createOpenFileDescriptor(@NotNull Project project, @NotNull XSourcePosition position) {
    return XDebuggerUtilImplShared.createOpenFileDescriptor(project, position);
  }
}

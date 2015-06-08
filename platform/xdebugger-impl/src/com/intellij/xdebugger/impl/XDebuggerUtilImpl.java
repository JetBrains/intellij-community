/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointFileGroupingRule;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class XDebuggerUtilImpl extends XDebuggerUtil {
  private XLineBreakpointType<?>[] myLineBreakpointTypes;
  private Map<Class<? extends XBreakpointType>, XBreakpointType<?,?>> myBreakpointTypeByClass;

  @Override
  public XLineBreakpointType<?>[] getLineBreakpointTypes() {
    if (myLineBreakpointTypes == null) {
      XBreakpointType[] types = XBreakpointUtil.getBreakpointTypes();
      List<XLineBreakpointType<?>> lineBreakpointTypes = new ArrayList<XLineBreakpointType<?>>();
      for (XBreakpointType type : types) {
        if (type instanceof XLineBreakpointType<?>) {
          lineBreakpointTypes.add((XLineBreakpointType<?>)type);
        }
      }
      myLineBreakpointTypes = lineBreakpointTypes.toArray(new XLineBreakpointType<?>[lineBreakpointTypes.size()]);
    }
    return myLineBreakpointTypes;
  }

  @Override
  public void toggleLineBreakpoint(@NotNull final Project project, @NotNull final VirtualFile file, final int line, boolean temporary) {
    XLineBreakpointType<?> typeWinner = null;
    for (XLineBreakpointType<?> type : getLineBreakpointTypes()) {
      if (type.canPutAt(file, line, project) && (typeWinner == null || type.getPriority() > typeWinner.getPriority())) {
        typeWinner = type;
      }
    }
    if (typeWinner != null) {
      toggleLineBreakpoint(project, typeWinner, file, line, temporary);
    }
  }

  @Override
  public boolean canPutBreakpointAt(@NotNull Project project, @NotNull VirtualFile file, int line) {
    for (XLineBreakpointType<?> type : getLineBreakpointTypes()) {
      if (type.canPutAt(file, line, project)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public <P extends XBreakpointProperties> void toggleLineBreakpoint(@NotNull final Project project,
                                                                     @NotNull final XLineBreakpointType<P> type,
                                                                     @NotNull final VirtualFile file,
                                                                     final int line,
                                                                     final boolean temporary) {
    toggleAndReturnLineBreakpoint(project, type, file, line, temporary);
  }

  public static <P extends XBreakpointProperties> XLineBreakpoint toggleAndReturnLineBreakpoint(@NotNull final Project project,
                                                                                                @NotNull final XLineBreakpointType<P> type,
                                                                                                @NotNull final VirtualFile file,
                                                                                                final int line,
                                                                                                final boolean temporary) {
    return new WriteAction<XLineBreakpoint>() {
      @Override
      protected void run(@NotNull final Result<XLineBreakpoint> result) {
        XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
        XLineBreakpoint<P> breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
        if (breakpoint != null) {
          breakpointManager.removeBreakpoint(breakpoint);
        }
        else {
          P properties = type.createBreakpointProperties(file, line);
          result.setResult(breakpointManager.addLineBreakpoint(type, file.getUrl(), line, properties, temporary));
        }
      }
    }.execute().getResultObject();
  }

  @Override
  public void removeBreakpoint(final Project project, final XBreakpoint<?> breakpoint) {
    new WriteAction() {
      @Override
      protected void run(@NotNull final Result result) {
        XDebuggerManager.getInstance(project).getBreakpointManager().removeBreakpoint(breakpoint);
      }
    }.execute();
  }

  @Override
  public <B extends XBreakpoint<?>> XBreakpointType<B, ?> findBreakpointType(@NotNull Class<? extends XBreakpointType<B, ?>> typeClass) {
    if (myBreakpointTypeByClass == null) {
      myBreakpointTypeByClass = new THashMap<Class<? extends XBreakpointType>, XBreakpointType<?,?>>();
      for (XBreakpointType<?, ?> breakpointType : XBreakpointUtil.getBreakpointTypes()) {
        myBreakpointTypeByClass.put(breakpointType.getClass(), breakpointType);
      }
    }
    XBreakpointType<?, ?> type = myBreakpointTypeByClass.get(typeClass);
    //noinspection unchecked
    return (XBreakpointType<B, ?>)type;
  }

  @Override
  public <T extends XDebuggerSettings<?>> T getDebuggerSettings(Class<T> aClass) {
    return XDebuggerSettingsManager.getInstanceImpl().getSettings(aClass);
  }

  @Override
  public XValueContainer getValueContainer(DataContext dataContext) {
    return XDebuggerTreeActionBase.getSelectedValue(dataContext);
  }

  @Override
  @Nullable
  public XSourcePosition createPosition(final VirtualFile file, final int line) {
    return XSourcePositionImpl.create(file, line);
  }

  @Override
  @Nullable
  public XSourcePosition createPositionByOffset(final VirtualFile file, final int offset) {
    return XSourcePositionImpl.createByOffset(file, offset);
  }

  @Override
  public <B extends XLineBreakpoint<?>> XBreakpointGroupingRule<B, ?> getGroupingByFileRule() {
    return new XBreakpointFileGroupingRule<B>();
  }

  @Nullable
  public static XSourcePosition getCaretPosition(@NotNull Project project, DataContext context) {
    Editor editor = getEditor(project, context);
    if (editor == null) return null;

    final Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return XSourcePositionImpl.createByOffset(file, offset);
  }

  @NotNull
  public static Collection<XSourcePosition> getAllCaretsPositions(@NotNull Project project, DataContext context) {
    Editor editor = getEditor(project, context);
    if (editor == null) {
      return Collections.emptyList();
    }

    final Document document = editor.getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    Collection<XSourcePosition> res = new ArrayList<XSourcePosition>();
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    for (Caret caret : carets) {
      int line = caret.getLogicalPosition().line;
      XSourcePositionImpl position = XSourcePositionImpl.create(file, line);
      if (position != null) {
        res.add(position);
      }
    }
    return res;
  }

  @Nullable
  private static Editor getEditor(@NotNull Project project, DataContext context) {
    Editor editor = CommonDataKeys.EDITOR.getData(context);
    if(editor == null) {
      return FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    return editor;
  }

  @Override
  public <B extends XBreakpoint<?>> Comparator<B> getDefaultBreakpointComparator(final XBreakpointType<B, ?> type) {
    return new Comparator<B>() {
      @Override
      public int compare(final B o1, final B o2) {
        return type.getDisplayText(o1).compareTo(type.getDisplayText(o2));
      }
    };
  }

  @Override
  public <P extends XBreakpointProperties> Comparator<XLineBreakpoint<P>> getDefaultLineBreakpointComparator() {
    return new Comparator<XLineBreakpoint<P>>() {
      @Override
      public int compare(final XLineBreakpoint<P> o1, final XLineBreakpoint<P> o2) {
        int fileCompare = o1.getFileUrl().compareTo(o2.getFileUrl());
        if (fileCompare != 0) return fileCompare;
        return o1.getLine() - o2.getLine();
      }
    };
  }

  @Nullable
  public static XDebuggerEvaluator getEvaluator(final XSuspendContext suspendContext) {
    XExecutionStack executionStack = suspendContext.getActiveExecutionStack();
    if (executionStack != null) {
      XStackFrame stackFrame = executionStack.getTopFrame();
      if (stackFrame != null) {
        return stackFrame.getEvaluator();
      }
    }
    return null;
  }

  @Override
  public void iterateLine(@NotNull Project project, @NotNull Document document, int line, @NotNull Processor<PsiElement> processor) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) {
      return;
    }

    int lineStart;
    int lineEnd;
    try {
      lineStart = document.getLineStartOffset(line);
      lineEnd = document.getLineEndOffset(line);
    }
    catch (IndexOutOfBoundsException ignored) {
      return;
    }

    PsiElement element;
    int offset = lineStart;

    if (file instanceof PsiCompiledFile) {
      file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    }

    while (offset < lineEnd) {
      element = file.findElementAt(offset);
      if (element != null) {
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
    return Collections.<XBreakpointGroupingRule<B, ?>>singletonList(this.<B>getGroupingByFileRule());
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

  @Nullable
  public static Editor createEditor(@NotNull OpenFileDescriptor descriptor) {
    return descriptor.canNavigate() ? FileEditorManager.getInstance(descriptor.getProject()).openTextEditor(descriptor, false) : null;
  }

  public static void rebuildAllSessionsViews(@Nullable Project project) {
    if (project == null) return;
    for (XDebugSession session : XDebuggerManager.getInstance(project).getDebugSessions()) {
      if (session.isSuspended()) {
        session.rebuildViews();
      }
    }
  }

  @NotNull
  @Override
  public XExpression createExpression(@NotNull String text, Language language, String custom, EvaluationMode mode) {
    return new XExpressionImpl(text, language, custom, mode);
  }

  public static boolean isEmptyExpression(@Nullable XExpression expression) {
    return expression == null || StringUtil.isEmptyOrSpaces(expression.getExpression());
  }
}

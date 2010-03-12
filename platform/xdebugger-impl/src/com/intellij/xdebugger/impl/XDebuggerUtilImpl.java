/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.ReflectionUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointFileGroupingRule;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class XDebuggerUtilImpl extends XDebuggerUtil {
  private XLineBreakpointType<?>[] myLineBreakpointTypes;
  private Map<Class<? extends XBreakpointType>, XBreakpointType<?,?>> myBreakpointTypeByClass;

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

  public void toggleLineBreakpoint(@NotNull final Project project, @NotNull final VirtualFile file, final int line) {
    for (XLineBreakpointType<?> type : getLineBreakpointTypes()) {
      if (type.canPutAt(file, line, project)) {
        toggleLineBreakpoint(project, type, file, line);
        return;
      }
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

  public <P extends XBreakpointProperties> void toggleLineBreakpoint(@NotNull final Project project, @NotNull final XLineBreakpointType<P> type, @NotNull final VirtualFile file,
                                                                     final int line) {
    new WriteAction() {
      protected void run(final Result result) {
        XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
        XLineBreakpoint<P> breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
        if (breakpoint != null) {
          breakpointManager.removeBreakpoint(breakpoint);
        }
        else {
          P properties = type.createBreakpointProperties(file, line);
          breakpointManager.addLineBreakpoint(type, file.getUrl(), line, properties);
        }
      }
    }.execute();
  }

  public void removeBreakpoint(final Project project, final XBreakpoint<?> breakpoint) {
    new WriteAction() {
      protected void run(final Result result) {
        XDebuggerManager.getInstance(project).getBreakpointManager().removeBreakpoint(breakpoint);
      }
    }.execute();
  }

  public <B extends XBreakpoint<?>> XBreakpointType<B, ?> findBreakpointType(@NotNull Class<? extends XBreakpointType<B, ?>> typeClass) {
    if (myBreakpointTypeByClass == null) {
      myBreakpointTypeByClass = new HashMap<Class<? extends XBreakpointType>, XBreakpointType<?,?>>();
      for (XBreakpointType<?, ?> breakpointType : XBreakpointUtil.getBreakpointTypes()) {
        myBreakpointTypeByClass.put(breakpointType.getClass(), breakpointType);
      }
    }
    XBreakpointType<?, ?> type = myBreakpointTypeByClass.get(typeClass);
    //noinspection unchecked
    return (XBreakpointType<B, ?>)type;
  }

  public <T extends XDebuggerSettings<?>> T getDebuggerSettings(Class<T> aClass) {
    return XDebuggerSettingsManager.getInstance().getSettings(aClass);
  }

  @Override
  public XValueContainer getValueContainer(DataContext dataContext) {
    return XDebuggerTreeActionBase.getSelectedValue(dataContext);
  }

  @Nullable
  public XSourcePosition createPosition(@NotNull final VirtualFile file, final int line) {
    return XSourcePositionImpl.create(file, line);
  }

  @Nullable 
  public XSourcePosition createPositionByOffset(@NotNull final VirtualFile file, final int offset) {
    return XSourcePositionImpl.createByOffset(file, offset);
  }

  public <B extends XLineBreakpoint<?>> XBreakpointGroupingRule<B, ?> getGroupingByFileRule() {
    return new XBreakpointFileGroupingRule<B>();
  }

  @Nullable
  public static XSourcePosition getCaretPosition(@NotNull Project project, DataContext context) {
    Editor editor = getEditor(project, context);
    if (editor == null) return null;

    final Document document = editor.getDocument();
    final int offset = editor.getCaretModel().getOffset();
    int line = document.getLineNumber(offset);

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return XSourcePositionImpl.create(file, line);
  }

  @Nullable
  private static Editor getEditor(@NotNull Project project, DataContext context) {
    Editor editor = PlatformDataKeys.EDITOR.getData(context);
    if(editor == null) {
      return FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    return editor;
  }

  public <B extends XBreakpoint<?>> Comparator<B> getDefaultBreakpointComparator(final XBreakpointType<B, ?> type) {
    return new Comparator<B>() {
      public int compare(final B o1, final B o2) {
        return type.getDisplayText(o1).compareTo(type.getDisplayText(o2));
      }
    };
  }

  public <P extends XBreakpointProperties> Comparator<XLineBreakpoint<P>> getDefaultLineBreakpointComparator() {
    return new Comparator<XLineBreakpoint<P>>() {
      public int compare(final XLineBreakpoint<P> o1, final XLineBreakpoint<P> o2) {
        int fileCompare = o1.getFileUrl().compareTo(o2.getFileUrl());
        if (fileCompare != 0) return fileCompare;
        return o1.getLine() - o2.getLine();
      }
    };
  }

  public static Class getStateClass(final Class<? extends PersistentStateComponent> aClass) {
    return ReflectionUtil.getRawType(ReflectionUtil.resolveVariableInHierarchy(PersistentStateComponent.class.getTypeParameters()[0], aClass));
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

  public void iterateLine(@NotNull Project project, @NotNull Document document, int line, @NotNull Processor<PsiElement> processor) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    int lineStart;
    int lineEnd;

    try {
      lineStart = document.getLineStartOffset(line);
      lineEnd = document.getLineEndOffset(line);
    }
    catch (IndexOutOfBoundsException e) {
      return;
    }

    PsiElement element;

    int off = lineStart;
    while (off < lineEnd) {
      element = file.findElementAt(off);
      if (element != null) {
        if (!processor.process(element)) {
          return;
        }
        else {
          off = element.getTextRange().getEndOffset();
        }
      }
      else {
        off++;
      }
    }
  }
}

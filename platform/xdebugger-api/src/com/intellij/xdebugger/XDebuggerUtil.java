/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.xdebugger;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public abstract class XDebuggerUtil {
  public static XDebuggerUtil getInstance() {
    return ServiceManager.getService(XDebuggerUtil.class);
  }

  public abstract XLineBreakpointType<?>[] getLineBreakpointTypes();

  public void toggleLineBreakpoint(@NotNull Project project, @NotNull VirtualFile file, int line) {
    toggleLineBreakpoint(project, file, line, false);
  }

  public abstract void toggleLineBreakpoint(@NotNull Project project,
                                            @NotNull VirtualFile file,
                                            int line,
                                            boolean temporary);

  public abstract boolean canPutBreakpointAt(@NotNull Project project, @NotNull VirtualFile file, int line);

  public <P extends XBreakpointProperties> void toggleLineBreakpoint(@NotNull Project project, @NotNull XLineBreakpointType<P> type,
                                                                     @NotNull VirtualFile file, int line) {
    toggleLineBreakpoint(project, type, file, line, false);
  }

  public abstract <P extends XBreakpointProperties> void toggleLineBreakpoint(@NotNull Project project,
                                                                              @NotNull XLineBreakpointType<P> type,
                                                                              @NotNull VirtualFile file,
                                                                              int line,
                                                                              boolean temporary);

  public abstract void removeBreakpoint(Project project, XBreakpoint<?> breakpoint);

  public abstract <T extends XBreakpointType> T findBreakpointType(@NotNull Class<T> typeClass);

  /**
   * Create {@link XSourcePosition} instance by line number
   * @param file file
   * @param line 0-based line number
   * @return source position
   */
  @Nullable
  public abstract XSourcePosition createPosition(@Nullable VirtualFile file, int line);

  /**
   * Create {@link XSourcePosition} instance by line and column number
   *
   * @param file   file
   * @param line   0-based line number
   * @param column 0-based column number
   * @return source position
   */
  @Nullable
  public abstract XSourcePosition createPosition(@Nullable VirtualFile file, int line, int column);

  /**
   * Create {@link XSourcePosition} instance by line number
   * @param file file
   * @param offset offset from the beginning of file
   * @return source position
   */
  @Nullable
  public abstract XSourcePosition createPositionByOffset(@Nullable VirtualFile file, int offset);

  @Nullable
  public abstract XSourcePosition createPositionByElement(@Nullable PsiElement element);

  public abstract <B extends XLineBreakpoint<?>> XBreakpointGroupingRule<B, ?> getGroupingByFileRule();

  public abstract <B extends XLineBreakpoint<?>> List<XBreakpointGroupingRule<B, ?>> getGroupingByFileRuleAsList();

  public abstract <B extends XBreakpoint<?>> Comparator<B> getDefaultBreakpointComparator(XBreakpointType<B, ?> type);

  public abstract <P extends XBreakpointProperties> Comparator<XLineBreakpoint<P>> getDefaultLineBreakpointComparator();

  public abstract <T extends XDebuggerSettings<?>> T getDebuggerSettings(Class<T> aClass);

  @Nullable
  public abstract XValueContainer getValueContainer(DataContext dataContext);

  /**
   * Process all {@link PsiElement}s on the specified line
   * @param project project
   * @param document document
   * @param line 0-based line number
   * @param processor processor
   */
  public abstract void iterateLine(@NotNull Project project, @NotNull Document document, int line, @NotNull Processor<PsiElement> processor);

  /**
   * Disable value lookup in specified editor
   */
  public abstract void disableValueLookup(@NotNull Editor editor);

  @Nullable
  public abstract PsiElement findContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project, boolean checkXml);

  @NotNull
  public abstract XExpression createExpression(@NotNull String text, Language language, String custom, EvaluationMode mode);

  public abstract void logStack(@NotNull XSuspendContext suspendContext, @NotNull XDebugSession session);
}

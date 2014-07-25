package com.jetbrains.javascript.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaScriptDebugAware {
  public static final ExtensionPointName<JavaScriptDebugAware> EP_NAME = ExtensionPointName.create("com.jetbrains.javaScriptDebugAware");

  @Nullable
  public abstract FileType getFileType();

  @Nullable
  public XLineBreakpointType<?> getBreakpointTypeClass(@NotNull Project project) {
    return null;
  }

  /**
   * Return false if you language could be natively executed in the VM
   * You must not specify it and it doesn't matter if you use not own breakpoint type - (Kotlin or GWT use java breakpoint type, for example)
   */
  public boolean isOnlySourceMappedBreakpoints() {
    return true;
  }

  @Nullable
  public TextRange getRangeForNamedElement(@NotNull PsiElement element, @Nullable PsiElement parent, int offset) {
    return null;
  }

  @Nullable
  public ExpressionInfo getEvaluationInfo(@NotNull PsiElement element, @NotNull Document document, @NotNull Project project) {
    return null;
  }

  @Nullable
  public static JavaScriptDebugAware find(@Nullable FileType fileType) {
    if (fileType == null) {
      return null;
    }

    for (JavaScriptDebugAware debugAware : EP_NAME.getExtensions()) {
      if (fileType.equals(debugAware.getFileType())) {
        return debugAware;
      }
    }
    return null;
  }

  public static boolean isBreakpointAware(@Nullable FileType fileType) {
    return find(fileType) != null;
  }
}
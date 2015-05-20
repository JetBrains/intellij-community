package com.jetbrains.javascript.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.MemberFilter;

public abstract class JavaScriptDebugAware {
  public static final ExtensionPointName<JavaScriptDebugAware> EP_NAME = ExtensionPointName.create("com.jetbrains.javaScriptDebugAware");

  @Nullable
  protected LanguageFileType getFileType() {
    return null;
  }

  @Nullable
  public Class<? extends XLineBreakpointType<?>> getBreakpointTypeClass() {
    return null;
  }

  /**
   * Return false if you language could be natively executed in the VM
   * You must not specify it and it doesn't matter if you use not own breakpoint type - (Kotlin or GWT use java breakpoint type, for example)
   */
  public boolean isOnlySourceMappedBreakpoints() {
    return true;
  }

  public final boolean canGetEvaluationInfo(@NotNull PsiFile file) {
    return file.getFileType().equals(getFileType());
  }

  @Nullable
  public final ExpressionInfo getEvaluationInfo(@NotNull PsiFile file, int offset, @NotNull Document document, @NotNull ExpressionInfoFactory expressionInfoFactory) {
    PsiElement element = file.findElementAt(offset);
    return element == null ? null : getEvaluationInfo(element, document, expressionInfoFactory);
  }

  @Nullable
  protected ExpressionInfo getEvaluationInfo(@NotNull PsiElement elementAtOffset, @NotNull Document document, @NotNull ExpressionInfoFactory expressionInfoFactory) {
    return null;
  }

  public static boolean isBreakpointAware(@NotNull FileType fileType) {
    return getBreakpointAware(fileType) != null;
  }

  @Nullable
  public static JavaScriptDebugAware getBreakpointAware(@NotNull FileType fileType) {
    for (JavaScriptDebugAware debugAware : EP_NAME.getExtensions()) {
      if (debugAware.getBreakpointTypeClass() == null && fileType.equals(debugAware.getFileType())) {
        return debugAware;
      }
    }
    return null;
  }

  @Nullable
  public MemberFilter createMemberFilter(@Nullable NameMapper nameMapper, @NotNull PsiElement element, int end) {
    return null;
  }

  @Nullable
  public PsiElement getNavigationElementForSourcemapInspector(@NotNull PsiFile file) {
    return null;
  }
}
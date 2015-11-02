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
    JavaScriptDebugAware aware = getBreakpointAware(fileType);
    return aware != null && aware.getBreakpointTypeClass() == null;
  }

  @Nullable
  public static JavaScriptDebugAware getBreakpointAware(@NotNull FileType fileType) {
    for (JavaScriptDebugAware debugAware : EP_NAME.getExtensions()) {
      if (fileType.equals(debugAware.getFileType())) {
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

  @Nullable
  // return null if unsupported
  // cannot be in MemberFilter because creation of MemberFilter could be async
  // the problem - GWT mangles name (https://code.google.com/p/google-web-toolkit/issues/detail?id=9106 https://github.com/sdbg/sdbg/issues/6 https://youtrack.jetbrains.com/issue/IDEA-135356), but doesn't add name mappings
  public String normalizeMemberName(@NotNull String name) {
    return null;
  }
}
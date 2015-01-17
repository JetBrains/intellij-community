package org.jetbrains.debugger;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebuggerSupportUtils {
  @Nullable
  public static PsiElement getContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project) {
    return XDebuggerUtil.getInstance().findContextElement(virtualFile, offset, project, true);
  }
}

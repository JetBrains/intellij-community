package org.jetbrains.debugger;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.Nullable;

public final class DebuggerSupportUtils {
  @Nullable
  public static XSourcePosition calcSourcePosition(@Nullable PsiElement element) {
    if (element != null) {
      PsiElement navigationElement = element.getNavigationElement();
      VirtualFile file = navigationElement.getContainingFile().getVirtualFile();
      if (file != null) {
        return XDebuggerUtil.getInstance().createPositionByOffset(file, navigationElement.getTextOffset());
      }
    }
    return null;
  }
}

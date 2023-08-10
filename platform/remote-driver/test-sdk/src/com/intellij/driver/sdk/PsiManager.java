package com.intellij.driver.sdk;

import com.intellij.driver.client.Remote;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Remote("com.intellij.psi.PsiManager")
public interface PsiManager {
  @Nullable PsiFile findFile(@NotNull VirtualFile file);
}

package com.intellij.driver.sdk.spring;

import com.intellij.driver.client.Remote;
import com.intellij.driver.sdk.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Remote(value = "com.intellij.spring.SpringManager", plugin = "com.intellij.spring")
public interface SpringManager {
  @Nullable SpringModel getSpringModelByFile(@NotNull PsiFile file);
}

package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface RootModelProvider {
  @NotNull
  Module[] getModules();

  ModuleRootModel getRootModel(@NotNull Module module);
}

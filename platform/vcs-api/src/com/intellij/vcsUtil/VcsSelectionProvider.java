package com.intellij.vcsUtil;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.actions.VcsContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface VcsSelectionProvider {
  ExtensionPointName<VcsSelectionProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcsSelectionProvider");

  @Nullable
  VcsSelection getSelection(VcsContext context);
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;

/**
 * An entry point to implement copyright support for the language
 */
public abstract class UpdateCopyrightsProvider {
  /**
   * @return {@link UpdateCopyright} for the file. In most cases, {@link UpdatePsiFileCopyright} would be used
   */
  public abstract UpdateCopyright createInstance(Project project,
                                                 Module module,
                                                 VirtualFile file,
                                                 FileType base,
                                                 CopyrightProfile options);

  public LanguageOptions getDefaultOptions() {
    return new LanguageOptions();
  }

  protected static LanguageOptions createDefaultOptions(boolean prefix) {
    LanguageOptions languageOptions = new LanguageOptions();
    languageOptions.setPrefixLines(prefix);
    return languageOptions;
  }
}

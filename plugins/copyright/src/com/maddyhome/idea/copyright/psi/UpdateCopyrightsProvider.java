// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;

/**
 * @author yole
 */
public abstract class UpdateCopyrightsProvider {
  public abstract UpdateCopyright createInstance(Project project, Module module, VirtualFile file,
                                                 FileType base, CopyrightProfile options);

  public LanguageOptions getDefaultOptions() {
    return new LanguageOptions();
  }

  protected static LanguageOptions createDefaultOptions(boolean prefix) {
    LanguageOptions languageOptions = new LanguageOptions();
    languageOptions.setPrefixLines(prefix);
    return languageOptions;
  }
}

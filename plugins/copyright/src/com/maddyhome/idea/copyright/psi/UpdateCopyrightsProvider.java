/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    final LanguageOptions languageOptions = new LanguageOptions();
    languageOptions.setPrefixLines(prefix);
    return languageOptions;
  }
}

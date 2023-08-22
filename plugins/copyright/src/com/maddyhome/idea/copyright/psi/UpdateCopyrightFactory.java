// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import org.jetbrains.annotations.Nullable;

public final class UpdateCopyrightFactory {
  private static final Logger LOG = Logger.getInstance(UpdateCopyrightFactory.class.getName());

  private UpdateCopyrightFactory() {
  }

  public static @Nullable UpdateCopyright createUpdateCopyright(Project project, Module module, PsiFile file, CopyrightProfile options) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    return createUpdateCopyright(project, module, virtualFile, virtualFile.getFileType(), options);
  }

  private static @Nullable UpdateCopyright createUpdateCopyright(Project project,
                                                                 Module module,
                                                                 VirtualFile file,
                                                                 FileType type,
                                                                 CopyrightProfile options) {
    // NOTE - any changes here require changes to LanguageOptionsFactory and ConfigTabFactory
    LOG.debug("file=" + file);
    LOG.debug("type=" + type.getName());
    UpdateCopyrightsProvider provider = CopyrightUpdaters.INSTANCE.forFileType(type);
    return provider != null ? provider.createInstance(project, module, file, type, options) : null;
  }
}
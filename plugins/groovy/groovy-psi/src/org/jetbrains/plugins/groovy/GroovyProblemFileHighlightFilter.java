// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

public class GroovyProblemFileHighlightFilter implements Condition<VirtualFile> {
  @Override
  public boolean value(VirtualFile virtualFile) {
    return FileTypeRegistry.getInstance().isFileOfType(virtualFile, GroovyFileType.GROOVY_FILE_TYPE);
  }
}

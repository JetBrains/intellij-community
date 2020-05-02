// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.CompilableFileTypesProvider;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Collections;
import java.util.Set;

public class GroovyCompilableFileTypesProvider implements CompilableFileTypesProvider {
  @Override
  public @NotNull Set<FileType> getCompilableFileTypes() {
    return Collections.singleton(GroovyFileType.GROOVY_FILE_TYPE);
  }
}

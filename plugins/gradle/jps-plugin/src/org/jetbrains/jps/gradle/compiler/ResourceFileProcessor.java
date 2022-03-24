// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.compiler;

import com.intellij.openapi.util.Ref;
import org.jetbrains.jps.gradle.model.impl.ResourceRootConfiguration;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

public interface ResourceFileProcessor {
  void copyFile(File file,
                Ref<File> targetFileRef,
                ResourceRootConfiguration rootConfiguration,
                CompileContext context,
                FileFilter filteringFilter) throws IOException;
}

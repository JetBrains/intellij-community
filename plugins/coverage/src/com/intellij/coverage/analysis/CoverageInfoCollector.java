// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.openapi.vfs.VirtualFile;

public interface CoverageInfoCollector {
  default void addSourceDirectory(VirtualFile virtualFile, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) { }

  default void addPackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, boolean flatten) { }

  default void addClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) { }
}

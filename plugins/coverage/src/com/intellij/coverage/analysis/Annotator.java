// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import com.intellij.openapi.vfs.VirtualFile;

public interface Annotator {
  default void annotateSourceDirectory(VirtualFile virtualFile, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) { }

  default void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) { }

  default void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo, boolean flatten) { }

  default void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) { }
}

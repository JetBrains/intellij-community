// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AnnotationGutterColumnProvider {
  ExtensionPointName<AnnotationGutterColumnProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.vcsAnnotationGutterColumnProvider");

  @Nullable
  LineAnnotationAspect createColumn(@NotNull FileAnnotation annotation);
}

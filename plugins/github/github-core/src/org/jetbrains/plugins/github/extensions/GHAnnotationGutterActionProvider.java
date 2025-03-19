// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.extensions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GHOpenInBrowserFromAnnotationActionGroup;

/**
 * @author Kirill Likhodedov
 */
public class GHAnnotationGutterActionProvider implements AnnotationGutterActionProvider {

  @Override
  public @NotNull AnAction createAction(@NotNull FileAnnotation annotation) {
    return new GHOpenInBrowserFromAnnotationActionGroup(annotation);
  }
}

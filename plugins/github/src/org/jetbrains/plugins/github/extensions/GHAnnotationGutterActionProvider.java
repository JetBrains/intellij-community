// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public AnAction createAction(@NotNull FileAnnotation annotation) {
    return new GHOpenInBrowserFromAnnotationActionGroup(annotation);
  }
}

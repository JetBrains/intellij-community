// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public interface AnnotationCustomConfigurator {
  ExtensionPointName<AnnotationCustomConfigurator> EP_NAME =
    ExtensionPointName.create("com.intellij.vcsAnnotationCustomProvider");
  void configure(@NotNull Editor editor);
}

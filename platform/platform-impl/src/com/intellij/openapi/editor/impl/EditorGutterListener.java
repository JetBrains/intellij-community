// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface EditorGutterListener extends EventListener {

  void textAnnotationAdded(@NotNull TextAnnotationGutterProvider provider);

  void textAnnotationRemoved(@NotNull TextAnnotationGutterProvider provider);
}

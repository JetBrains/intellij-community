// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.parser;


import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;

public interface GradleDslConverterFactory {
  ExtensionPointName<GradleDslConverterFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.gradle.dsl.parserFactory");

  boolean canConvert(PsiFile psiFile);

  GradleDslWriter createWriter();

  GradleDslParser createParser(PsiFile psiFile, GradleDslFile gradleDslFile);
}

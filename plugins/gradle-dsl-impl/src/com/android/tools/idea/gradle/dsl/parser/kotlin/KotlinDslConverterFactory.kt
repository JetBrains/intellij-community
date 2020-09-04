// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.parser.GradleDslConverterFactory
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

class KotlinDslConverterFactory : GradleDslConverterFactory {
  override fun canConvert(psiFile: PsiFile) = psiFile is KtFile

  override fun createWriter(): GradleDslWriter = KotlinDslWriter()

  override fun createParser(psiFile: PsiFile, gradleDslFile: GradleDslFile) = KotlinDslParser(psiFile as KtFile, gradleDslFile)

}
package com.intellij.compose.ide.plugin

import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsFilter
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

/**
 * Extension for [OverrideImplementsAnnotationsFilter], which checks if the "Composable" annotation is on the classpath,
 * and if that's the case - retains it while doing overrides.
 * This is more generic than the Android's `com.android.tools.compose.ComposeOverrideImplementsAnnotationsFilter` that only checks
 * module's `usesCompose` flag, which only works for Android modules, but not for multiplatform.
 */
internal class ComposeOverrideImplementsAnnotationsFilter : OverrideImplementsAnnotationsFilter {
  override fun getAnnotations(file: PsiFile): Array<String> {
    return if (file is KtFile && isKotlinClassAvailable(file, COMPOSABLE_ANNOTATION_CLASS_ID)) {
      arrayOf(COMPOSABLE_ANNOTATION_FQ_NAME.asString())
    } else {
      arrayOf()
    }
  }
}

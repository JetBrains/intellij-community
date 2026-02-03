// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi.fileTypes

import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinLabelProviderService
import org.jetbrains.kotlin.idea.base.psi.KotlinBasePsiBundle

private class IdeKotlinLabelProviderService : KotlinLabelProviderService() {
    override fun getLabelForBuiltInFileType(): String = KotlinBasePsiBundle.message("kotlin.built.in.file.type")
    override fun getLabelForKlibMetaFileType(): String = KotlinBasePsiBundle.message("klib.metadata.short")
    override fun getLabelForKotlinJavaScriptMetaFileType(): String = KotlinBasePsiBundle.message("kotlin.javascript.meta.file")
}

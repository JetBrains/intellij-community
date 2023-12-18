// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmAnnotation
import com.intellij.lang.jvm.types.JvmType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtType

/**
 * A [JvmType] whose only purpose is to represent a [KtType]. Note that this class does not have any information
 * other than [KtType]. For example, there is no guarantee that [JvmTypeWrapperForKtType.getAnnotations]
 * returns any annotations.
 *
 * Note that our "request and creation" architecture will support the cross language requests and creations like J2K, K2K, and so on.
 * For the cross language support, we use [JvmType]. However, for K2K, the conversion between [KtType] and [JvmType] is not
 * unnecessary because both the request and creation sides use [KtType]. This class helps us to avoid the unnecessary conversion.
 */
context (KtAnalysisSession)
class JvmTypeWrapperForKtType(val ktType: KtType) : JvmType {
    override fun getAnnotations(): Array<JvmAnnotation> = emptyArray()
}
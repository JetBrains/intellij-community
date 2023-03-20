// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ChangeInfo

class KotlinChangeInfoWrapper(delegate: KotlinChangeInfo) : ChangeInfo {
    var delegate: KotlinChangeInfo? = delegate
        private set

    private val method = delegate.method

    override fun getMethod() = method

    override fun isGenerateDelegate() = delegate!!.isGenerateDelegate

    override fun getNewName() = delegate!!.newName

    override fun isParameterTypesChanged() = delegate!!.isParameterTypesChanged

    override fun getNewParameters() = delegate!!.newParameters

    override fun isParameterSetOrOrderChanged() = delegate!!.isParameterSetOrOrderChanged

    override fun isReturnTypeChanged() = delegate!!.isReturnTypeChanged

    override fun isParameterNamesChanged() = delegate!!.isParameterNamesChanged

    override fun isNameChanged() = delegate!!.isNameChanged

    override fun getLanguage() = delegate!!.language

    // Only getMethod() may be called after invalidate()
    fun invalidate() {
        delegate = null
    }
}
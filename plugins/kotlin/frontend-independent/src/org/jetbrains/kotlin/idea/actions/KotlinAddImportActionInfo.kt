// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.psi.UserDataProperty

/** Test hooks allowing inspection of data used for KotlinAddImportAction. **/
object KotlinAddImportActionInfo {
    interface ExecuteListener {
        fun onExecute(variants: List<AutoImportVariant>)
    }

    var PsiFile.executeListener: ExecuteListener? by UserDataProperty(Key("KOTLIN_IMPORT_EXECUTE_LISTENER"))

    @TestOnly
    fun setExecuteListener(file: PsiFile, disposable: Disposable, listener: ExecuteListener) {
        assert(file.executeListener == null)
        file.executeListener = listener
        Disposer.register(disposable) { file.executeListener = null }
    }
}
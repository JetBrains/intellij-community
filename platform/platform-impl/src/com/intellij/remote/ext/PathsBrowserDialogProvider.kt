// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.remote.RemoteSdkAdditionalData
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.JTextField

/**
 * Could be implemented by [com.intellij.remote.CredentialsType] to allow
 * browsing for paths fields in create remote SDK dialog.
 */
interface PathsBrowserDialogProvider {
  fun showPathsBrowserDialog(project: Project?,
                             textField: JTextField,
                             dialogTitle: @NlsContexts.DialogTitle String,
                             supplier: Supplier<out RemoteSdkAdditionalData<*>>)
}

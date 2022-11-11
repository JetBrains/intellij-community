// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

const val KOTLIN_WORKSHEET_EXTENSION: String = "ws.kts"

class NewKotlinWorksheetAction : NewKotlinScriptAction(
    actionName = KotlinBundle.message("action.new.worksheet.name"),
    description = KotlinBundle.message("action.new.worksheet.description"),
    dialogTitle = KotlinBundle.message("action.new.worksheet.dialog.title"),
    templateName = "Kotlin Worksheet"
) {

    override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
        val kotlinWorksheetTemplate = object : FileTemplate by template {
            override fun getExtension(): String = KOTLIN_WORKSHEET_EXTENSION
        }

        return super.createFileFromTemplate(name, kotlinWorksheetTemplate, dir)
    }
}
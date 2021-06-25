// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.editor

import com.intellij.application.options.editor.*
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.layout.*
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinBundle.message
import javax.swing.JComponent

private val editorOptions = KotlinEditorOptions.getInstance()

private val cbConvertPastedJavaToKotlin
    get() = CheckboxDescriptor(
        message("editor.checkbox.title.convert.pasted.java.code.to.kotlin"),
        PropertyBinding(
            editorOptions::isEnableJavaToKotlinConversion,
            editorOptions::setEnableJavaToKotlinConversion
        )
    )

private val cbDontShowJavaToKotlinConversionDialog
    get() = CheckboxDescriptor(
        message("editor.checkbox.title.don.t.show.java.to.kotlin.conversion.dialog.on.paste"),
        PropertyBinding(
            editorOptions::isDonTShowConversionDialog,
            editorOptions::setDonTShowConversionDialog
        )
    )

private val cbAutoAddValKeywordToCtorParameters
    get() = CheckboxDescriptor(
        message("editor.checkbox.title.auto.add.val.keyword.to.data.inline.class.constructor.parameters"),
        PropertyBinding(
            editorOptions::isAutoAddValKeywordToDataClassParameters,
            editorOptions::setAutoAddValKeywordToDataClassParameters
        )
    )

@NonNls
const val ID = "editor.title.kotlin"

internal val kotlinEditorOptionsDescriptors
    get() = sequenceOf(
        cbConvertPastedJavaToKotlin,
        cbDontShowJavaToKotlinConversionDialog,
        cbAutoAddValKeywordToCtorParameters
    ).map(CheckboxDescriptor::asUiOptionDescriptor)

class KotlinEditorOptionsConfigurable : BeanConfigurable<KotlinEditorOptions?>(KotlinEditorOptions.getInstance()),
                                        UnnamedConfigurable {
    override fun createComponent(): JComponent {
        val component = super.createComponent()!!
        component.border = IdeBorderFactory.createTitledBorder(message(ID))
        return component
    }

    init {
        checkBox(cbConvertPastedJavaToKotlin)
        checkBox(cbDontShowJavaToKotlinConversionDialog)
        checkBox(cbAutoAddValKeywordToCtorParameters)
    }

    private fun checkBox(checkboxDescriptor: CheckboxDescriptor) {
        checkBox(checkboxDescriptor.name, { checkboxDescriptor.binding.get() }) {
            checkboxDescriptor.binding.set(it)
        }
    }
}
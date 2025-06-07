// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceProperty

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.NonFocusableCheckBox
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
import org.jetbrains.kotlin.idea.refactoring.introduce.TYPE_REFERENCE_VARIABLE_NAME
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionResult
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionTarget
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.generateDeclaration
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.processDuplicatesSilently
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.AbstractKotlinInplaceVariableIntroducer
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import java.awt.event.ItemEvent
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

class KotlinInplacePropertyIntroducer(
    property: KtProperty,
    editor: Editor,
    project: Project,
    @Nls title: String,
    doNotChangeVar: Boolean,
    exprType: KotlinType?,
    private var extractionResult: ExtractionResult,
    private val availableTargets: List<ExtractionTarget>
) : AbstractKotlinInplaceVariableIntroducer<KtProperty, KotlinType>(
    property, editor, project, title, KtExpression.EMPTY_ARRAY, null, false, property, false, doNotChangeVar, exprType, false
) {
    init {
        assert(availableTargets.isNotEmpty()) { "No targets available: ${property.getElementTextWithContext()}" }
    }

    override fun renderType(kotlinType: KotlinType): String {
        return IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(kotlinType)
    }

    private var currentTarget: ExtractionTarget = extractionResult.config.generatorOptions.target
        set(value: ExtractionTarget) {
            if (value == currentTarget) return

            field = value
            runWriteActionAndRestartRefactoring {
                with(extractionResult.config) {
                    extractionResult = copy(generatorOptions = generatorOptions.copy(target = currentTarget)).generateDeclaration(property)
                    property = extractionResult.declaration as KtProperty
                    myElementToRename = property
                }
            }
            updatePanelControls()
        }

    private var replaceAll: Boolean = true

    private var property: KtProperty
        get() = myDeclaration
        set(value: KtProperty) {
            myDeclaration = value
        }

    private fun isInitializer(): Boolean = currentTarget == ExtractionTarget.PROPERTY_WITH_INITIALIZER

    override fun initPanelControls() {
        if (availableTargets.size > 1) {
            addPanelControl(
                ControlWrapper {
                    val propertyKindComboBox = with(JComboBox(availableTargets.map { it.targetName.capitalize() }.toTypedArray())) {
                        addItemListener { itemEvent ->
                            if (itemEvent.stateChange == ItemEvent.SELECTED) {
                                ApplicationManager.getApplication().invokeLater {
                                    currentTarget = availableTargets[selectedIndex]
                                }
                            }
                        }

                        selectedIndex = availableTargets.indexOf(currentTarget)

                        this
                    }

                    val propertyKindLabel = JLabel(KotlinBundle.message("label.text.introduce.as"))
                    propertyKindLabel.labelFor = propertyKindComboBox

                    val panel = JPanel()
                    panel.add(propertyKindLabel)
                    panel.add(propertyKindComboBox)

                    panel
                }
            )
        }

        if (ExtractionTarget.PROPERTY_WITH_INITIALIZER in availableTargets) {
            val condition = { isInitializer() }

            createVarCheckBox?.let {
                addPanelControl(ControlWrapper(it, condition
                ) {
                    (it as JCheckBox).isSelected = property.isVar
                })
            }
            createExplicitTypeCheckBox?.let {
                addPanelControl(ControlWrapper(it, condition
                ) {
                    (it as JCheckBox).isSelected = property.typeReference != null
                })
            }
        }

        val occurrenceCount = extractionResult.duplicateReplacers.size
        if (occurrenceCount > 1) {
            addPanelControl(
                ControlWrapper {
                    val replaceAllCheckBox = NonFocusableCheckBox(
                        KotlinBundle.message("checkbox.text.replace.all.occurrences.0", occurrenceCount))
                    replaceAllCheckBox.isSelected = replaceAll
                    replaceAllCheckBox.addActionListener { replaceAll = replaceAllCheckBox.isSelected }
                    replaceAllCheckBox
                }
            )
        }
    }

    override fun addTypeReferenceVariable(builder: TemplateBuilderImpl) {
        if (!isInitializer()) return
        val typeReference = myDeclaration.getTypeReference();
        val exprType = myExprType
        if (exprType != null) {
            val expression = SpecifyTypeExplicitlyIntention.Companion.createTypeExpressionForTemplate(exprType, myDeclaration, false);
            if (typeReference != null && expression != null) {
                builder.replaceElement(typeReference, TYPE_REFERENCE_VARIABLE_NAME, expression, false);
            }
        }
    }

    override fun createTypeReferencePostprocessor(): TemplateEditingListener {
        return SpecifyTypeExplicitlyIntention.Companion.createTypeReferencePostprocessor(myDeclaration)
    }

    override fun checkLocalScope(): PsiElement {
        return myElementToRename.parentsWithSelf.first { it is KtClassOrObject || it is KtFile }
    }

    override fun performRefactoring(): Boolean {
        if (replaceAll) {
            processDuplicatesSilently(extractionResult.duplicateReplacers, myProject)
        }
        return true
    }
}
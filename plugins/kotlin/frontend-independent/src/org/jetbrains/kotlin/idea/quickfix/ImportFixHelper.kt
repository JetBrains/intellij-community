// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import java.awt.BorderLayout
import java.util.*
import javax.swing.JPanel
import javax.swing.ListCellRenderer

object ImportFixHelper {
    enum class ImportKind(private val key: String, val groupedByPackage: Boolean = false) {
        CLASS("text.class.0", true),
        ENUM_ENTRY("text.enum.entry.0"),
        TYPE_ALIAS("text.type.alias.0", true),
        PROPERTY("text.property.0"),
        OBJECT("text.object.0", true),
        FUNCTION("text.function.0"),
        EXTENSION_PROPERTY("text.extension.property.0"),
        EXTENSION_FUNCTION("text.extension.function.0"),
        INFIX_FUNCTION("text.infix.function.0"),
        INFIX_EXTENSION_FUNCTION("text.infix.extension.function.0"),
        OPERATOR("text.operator.0");

        fun toText(number: Int): String = KotlinBundle.message(key, if (number == 1) 1 else 2)
    }

    class ImportInfo<T : Comparable<T>>(val kind: ImportKind, val name: String, val priority: T)

    @IntentionName
    fun <T : Comparable<T>> calculateTextForFix(importInfos: Iterable<ImportInfo<T>>, suggestions: Iterable<FqName>): String {
        val importNamesGroupedByKind = importInfos.groupBy(keySelector = { it.kind }) { it }

        return if (importNamesGroupedByKind.size == 1) {
            val (kind, names) = importNamesGroupedByKind.entries.first()
            val sortedImportInfos = TreeSet<ImportInfo<T>>(compareBy({ it.priority }, { it.name }))
            sortedImportInfos.addAll(names)
            val firstName = sortedImportInfos.first().name
            val singlePackage = suggestionsAreFromSameParent(suggestions)

            if (singlePackage) {
                val sortedByName = sortedImportInfos.toSortedSet(compareBy { it.name })
                val size = sortedByName.size
                if (size == 2) {
                    KotlinBundle.message(
                        "fix.import.kind.0.name.1.and.name.2",
                        kind.toText(size),
                        sortedByName.first().name,
                        sortedByName.last().name
                    )
                } else {
                    KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(size), firstName, size - 1)
                }
            } else if (kind.groupedByPackage) {
                KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(1), firstName, 0)
            } else {
                val groupBy = sortedImportInfos.map { it.name }.toSortedSet().groupBy { it.substringBefore('.') }
                val value = groupBy.entries.first().value
                val first = value.first()
                val multiple = if (value.size == 1) 0 else 1
                when {
                    groupBy.size != 1 -> KotlinBundle.message(
                        "fix.import.kind.0.name.1.2",
                        kind.toText(1),
                        first.substringAfter('.'),
                        multiple
                    )

                    value.size == 2 -> KotlinBundle.message(
                        "fix.import.kind.0.name.1.and.name.2",
                        kind.toText(value.size),
                        first,
                        value.last()
                    )

                    else -> KotlinBundle.message("fix.import.kind.0.name.1.2", kind.toText(1), first, multiple)
                }
            }
        } else {
            KotlinBundle.message("fix.import")
        }
    }

    fun calculateWeightBasedOnFqName(fqName: FqName, sourceDeclaration: PsiElement?): Int {
        val fqNameString = fqName.asString()
        return when {
            /**
             * package rating calculation rule:
             * - (highest) current project source
             * - `kotlin.` and `kotlinx.`
             * - `java.`
             * - (lowest) all other 3rd party libs
             */
            fqNameString.startsWith("kotlin.") -> 6
            fqNameString.startsWith("kotlinx.") -> 5
            fqNameString.startsWith("java.") -> 2
            sourceDeclaration != null -> {
                val virtualFile = sourceDeclaration.containingFile?.virtualFile
                val fileIndex = ProjectRootManager.getInstance(sourceDeclaration.project).fileIndex
                // project source higher than libs
                if (virtualFile != null && fileIndex.isInSourceContent(virtualFile)) 7 else 0
            }

            else -> 0
        }
    }

    fun createListPopupWithImportVariants(
        project: Project,
        variants: List<AutoImportVariant>,
        addImport: (AutoImportVariant) -> Unit,
    ): ListPopup =
        JBPopupFactory.getInstance().createListPopup(project, getVariantSelectionPopup(project, variants, addImport)) {
            val psiRenderer = DefaultPsiElementCellRenderer()

            ListCellRenderer<AutoImportVariant> { list, value, index, isSelected, cellHasFocus ->
                JPanel(BorderLayout()).apply {
                    add(
                        psiRenderer.getListCellRendererComponent(
                            list,
                            value.declarationToImport,
                            index,
                            isSelected,
                            cellHasFocus
                        )
                    )
                }
            }
        }

    private fun getVariantSelectionPopup(
        project: Project,
        variants: List<AutoImportVariant>,
        addImport: (AutoImportVariant) -> Unit,
    ): BaseListPopupStep<AutoImportVariant> {
        return object : BaseListPopupStep<AutoImportVariant>(KotlinBundle.message("action.add.import.chooser.title"), variants) {
            override fun isAutoSelectionEnabled(): Boolean = false

            override fun isSpeedSearchEnabled(): Boolean = true

            override fun onChosen(selectedValue: AutoImportVariant?, finalChoice: Boolean): PopupStep<String>? {
                if (selectedValue == null || project.isDisposed) return null

                if (finalChoice) {
                    addImport(selectedValue)
                    return null
                }

                val toExclude = AddImportAction.getAllExcludableStrings(selectedValue.fqName.asString())

                return object : BaseListPopupStep<String>(null, toExclude) {
                    override fun getTextFor(value: String): String {
                        return KotlinBundle.message("fix.import.exclude", value)
                    }

                    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<Any>? {
                        if (finalChoice && !project.isDisposed) {
                            AddImportAction.excludeFromImport(project, selectedValue)
                        }
                        return null
                    }
                }
            }

            override fun hasSubstep(selectedValue: AutoImportVariant?) = true
            override fun getTextFor(value: AutoImportVariant) = value.hint
            override fun getIconFor(value: AutoImportVariant) = value.icon
        }
    }

    fun suggestionsAreFromSameParent(suggestions: Iterable<FqName>): Boolean =
        suggestions.distinctBy { it.parentOrNull() ?: FqName.ROOT }.size == 1
}



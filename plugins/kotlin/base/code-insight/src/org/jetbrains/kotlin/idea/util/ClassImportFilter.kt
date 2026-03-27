package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

/** Filter that can block classes from being automatically imported as a side-effect of other actions. */
fun interface ClassImportFilter {
    /**
     * This class holds information that implementations of ClassImportFilter might need to decide whether to import a given class.
     * By packaging this information in a data class, we avoid the need to change the API when we need to add more/different information.
     */
    data class ClassInfo(val fqName: FqName, val classKind: ClassKind, val modality: Modality, val visibility: Visibility, val isNested: Boolean)

    /** Returns whether to allow this class to be imported. */
    fun allowClassImport(classInfo: ClassInfo, contextFile: KtFile): Boolean

    companion object {
        val EP_NAME = ExtensionPointName.create<ClassImportFilter>("org.jetbrains.kotlin.classImportFilter")

        fun allowClassImport(fqName: FqName,
                             kaClassKind: KaClassKind,
                             symbolModality: KaSymbolModality,
                             symbolVisibility: KaSymbolVisibility,
                             isNested: Boolean,
                             contextFile: KtFile): Boolean {
            val classKind = when (kaClassKind) {
                KaClassKind.CLASS -> ClassKind.CLASS
                KaClassKind.ENUM_CLASS -> ClassKind.ENUM_CLASS
                KaClassKind.ANNOTATION_CLASS -> ClassKind.ANNOTATION_CLASS
                KaClassKind.INTERFACE -> ClassKind.INTERFACE
                KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT, KaClassKind.ANONYMOUS_OBJECT -> ClassKind.OBJECT
            }

            val modality = when (symbolModality) {
                KaSymbolModality.FINAL -> Modality.FINAL
                KaSymbolModality.SEALED -> Modality.SEALED
                KaSymbolModality.OPEN -> Modality.OPEN
                KaSymbolModality.ABSTRACT -> Modality.ABSTRACT
            }

            val visibility = when (symbolVisibility) {
                KaSymbolVisibility.PUBLIC -> Visibilities.Public
                KaSymbolVisibility.PROTECTED, KaSymbolVisibility.PACKAGE_PROTECTED -> Visibilities.Protected
                KaSymbolVisibility.PACKAGE_PRIVATE, KaSymbolVisibility.INTERNAL -> Visibilities.Internal
                KaSymbolVisibility.PRIVATE -> Visibilities.Private
                KaSymbolVisibility.LOCAL -> Visibilities.Local
                KaSymbolVisibility.UNKNOWN -> Visibilities.Public
            }

            return allowClassImport(ClassInfo(fqName, classKind, modality, visibility, isNested), contextFile)
        }

        fun allowClassImport(classInfo: ClassInfo, contextFile: KtFile): Boolean =
            EP_NAME.extensions.all { it.allowClassImport(classInfo, contextFile) }
    }
}

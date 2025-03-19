package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
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
    fun allowClassImport(classInfo: ClassInfo, contextFile: KtFile) : Boolean

    companion object {
        val EP_NAME = ExtensionPointName.create<ClassImportFilter>("org.jetbrains.kotlin.classImportFilter")
        fun allowClassImport(classInfo: ClassInfo, contextFile: KtFile) =
            EP_NAME.extensions.all { it.allowClassImport(classInfo, contextFile) }
    }
}
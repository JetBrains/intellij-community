package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtFile

/** Filter that can block classes from being automatically imported as a side-effect of other actions. */
fun interface ClassImportFilter {
    /** Returns whether to allow this class to be imported. */
    fun allowClassImport(descriptor: DeclarationDescriptor, contextFile: KtFile) : Boolean

    companion object {
        val EP_NAME = ExtensionPointName.create<ClassImportFilter>("org.jetbrains.kotlin.classImportFilter")
        fun allowClassImport(descriptor: DeclarationDescriptor, contextFile: KtFile) =
            EP_NAME.extensions.all { it.allowClassImport(descriptor, contextFile) }
    }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.KotlinModuleFileType
import org.jetbrains.kotlin.idea.base.psi.fileTypes.KlibMetaFileType
import org.jetbrains.kotlin.idea.base.psi.fileTypes.KotlinJavaScriptMetaFileType

abstract class KotlinBinaryExtension(val fileType: FileType) {
    companion object {
        val EP_NAME: ExtensionPointName<KotlinBinaryExtension> = ExtensionPointName.create("org.jetbrains.kotlin.binaryExtension")

        val kotlinBinaries: List<FileType> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            EP_NAME.extensionList.map { it.fileType }
        }
    }
}

class JavaClassBinary : KotlinBinaryExtension(JavaClassFileType.INSTANCE)
class KotlinBuiltInBinary : KotlinBinaryExtension(KotlinBuiltInFileType)
class KotlinModuleBinary : KotlinBinaryExtension(KotlinModuleFileType.INSTANCE)
class KotlinJsMetaBinary : KotlinBinaryExtension(KotlinJavaScriptMetaFileType)
class KlibMetaBinary : KotlinBinaryExtension(KlibMetaFileType)

val FileType.isKotlinBinary: Boolean
    get() = this in KotlinBinaryExtension.kotlinBinaries
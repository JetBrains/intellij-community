// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.klib

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.compiled.ClassFileStubBuilder
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.indexing.FileContent
import org.jetbrains.kotlin.analysis.decompiler.psi.text.DecompiledText
import org.jetbrains.kotlin.analysis.decompiler.stub.createIncompatibleAbiVersionFileStub
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.stubs.elements.KtFileElementType
import org.jetbrains.kotlin.serialization.SerializerExtensionProtocol
import org.jetbrains.kotlin.serialization.deserialization.FlexibleTypeDeserializer
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer

abstract class KlibMetadataStubBuilder(
    private val version: Int,
    private val fileType: FileType,
    private val serializerProtocol: () -> SerializerExtensionProtocol,
    private val readFile: (VirtualFile) -> FileWithMetadata?
) : ClsStubBuilder() {
    override fun getStubVersion() = ClassFileStubBuilder.STUB_VERSION + version

    protected abstract fun getDecompiledText(
        file: FileWithMetadata.Compatible,
        serializerProtocol: SerializerExtensionProtocol,
        flexibleTypeDeserializer: FlexibleTypeDeserializer
    ): DecompiledText

    override fun buildFileStub(content: FileContent): PsiFileStub<*>? {
        val virtualFile = content.file
        assert(FileTypeRegistry.getInstance().isFileOfType(virtualFile, fileType)) { "Unexpected file type ${virtualFile.fileType}" }

        val file = readFile(virtualFile) ?: return null

        return when (file) {
            is FileWithMetadata.Incompatible -> createIncompatibleAbiVersionFileStub()
            is FileWithMetadata.Compatible -> { //todo: this part is implemented in our own way
                val ktFileText = getDecompiledText(file, serializerProtocol(), DynamicTypeDeserializer)
                createFileStub(content.project, ktFileText.text)
            }
        }
    }
}

private fun createFileStub(project: Project, text: String): PsiFileStub<*> {
    val virtualFile = LightVirtualFile("dummy.kt", KotlinFileType.INSTANCE, text)
    virtualFile.language = KotlinLanguage.INSTANCE
    SingleRootFileViewProvider.doNotCheckFileSizeLimit(virtualFile)

    val psiFileFactory = PsiFileFactory.getInstance(project) as PsiFileFactoryImpl
    val file = psiFileFactory.trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, false, false)!!
    return KtFileElementType.INSTANCE.builder.buildStubTree(file) as PsiFileStub<*>
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.vfilefinder.FqNameKeyDescriptor
import org.jetbrains.kotlin.idea.vfilefinder.KotlinPartialPackageNamesIndex
import org.jetbrains.kotlin.load.kotlin.KotlinBinaryClassCache
import org.jetbrains.kotlin.load.kotlin.KotlinClassFinder
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import java.io.DataInput
import java.io.DataOutput

@ApiStatus.Internal
abstract class NameByPackageShortNameIndex : FileBasedIndexExtension<FqName, List<Name>>() {
    private val LOG = logger<KotlinPartialPackageNamesIndex>()

    protected abstract fun getDeclarationNamesByKtFile(ktFile: KtFile): List<Name>
    protected abstract fun getDeclarationNamesByMetadata(kotlinJvmBinaryClass: KotlinJvmBinaryClass): List<Name>
    protected abstract fun getPackageAndNamesFromBuiltIns(fileContent: FileContent): Map<FqName, List<Name>>

    override fun dependsOnFileContent() = true
    override fun getVersion() = 1
    override fun getKeyDescriptor() = FqNameKeyDescriptor
    override fun getValueExternalizer(): DataExternalizer<List<Name>> = ListOfNamesDataExternalizer
    override fun traceKeyHashToVirtualFileMapping(): Boolean = true

    override fun getInputFilter(): DefaultFileTypeSpecificInputFilter =
        DefaultFileTypeSpecificInputFilter(KotlinFileType.INSTANCE, JavaClassFileType.INSTANCE, KotlinBuiltInFileType)

    override fun getIndexer() = DataIndexer<FqName, List<Name>, FileContent> { fileContent ->
        try {
            when (fileContent.fileType) {
                JavaClassFileType.INSTANCE -> getPackageAndNamesFromMetadata(fileContent)
                KotlinBuiltInFileType -> getPackageAndNamesFromBuiltIns(fileContent)
                KotlinFileType.INSTANCE -> {
                    val ktFile = fileContent.psiFile as? KtFile ?: return@DataIndexer emptyMap()
                    mapOf(ktFile.packageFqName to getDeclarationNamesByKtFile(ktFile).distinct())
                }
                else -> emptyMap()
            }
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            LOG.warn("Error `(${e.javaClass.simpleName}: ${e.message})` while indexing file ${fileContent.fileName} using $name index. Probably the file is broken.")
            emptyMap()
        }
    }

    private fun getPackageAndNamesFromMetadata(fileContent: FileContent): Map<FqName, List<Name>> {
        val result = KotlinBinaryClassCache.getKotlinBinaryClassOrClassFileContent(fileContent.file, fileContent.content)
            ?: return emptyMap()
        val kotlinClass = result as? KotlinClassFinder.Result.KotlinClass ?: return emptyMap()
        val binaryClass = kotlinClass.kotlinJvmBinaryClass
        val packageName = binaryClass.classHeader.packageName?.let(::FqName) ?: binaryClass.classId.packageFqName
        if (binaryClass.classHeader.kind == KotlinClassHeader.Kind.SYNTHETIC_CLASS) return emptyMap()
        if (binaryClass.classId.isLocal) return emptyMap()
        return mapOf(packageName to getDeclarationNamesByMetadata(binaryClass).distinct())
    }

    fun getNamesInPackage(packageFqName: FqName, scope: GlobalSearchScope): Set<Name> = buildSet {
        FileBasedIndex.getInstance().getValues(name, packageFqName, scope).forEach(::addAll)
    }
}

private object ListOfNamesDataExternalizer : DataExternalizer<List<Name>> {
    override fun read(input: DataInput): List<Name> =
        IOUtil.readStringList(input).map(Name::identifier)

    override fun save(out: DataOutput, value: List<Name>) =
        IOUtil.writeStringList(out, value.map(Name::asString))
}
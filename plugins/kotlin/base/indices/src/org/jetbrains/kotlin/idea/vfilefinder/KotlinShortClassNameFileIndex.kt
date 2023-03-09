// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinMetadataStubBuilder
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.serialization.deserialization.getClassId
import java.io.DataInput
import java.io.DataOutput

class KotlinShortClassNameFileIndex : FileBasedIndexExtension<String, Collection<String>>() {
    companion object {
        val NAME: ID<String, Collection<String>> = ID.create(KotlinShortClassNameFileIndex::class.java.canonicalName)
    }

    override fun getName(): ID<String, Collection<String>> = NAME

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): EnumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer() = StringListExternalizer

    override fun getInputFilter(): DefaultFileTypeSpecificInputFilter =
        DefaultFileTypeSpecificInputFilter(
            KotlinFileType.INSTANCE,
            KotlinBuiltInFileType,
            JavaClassFileType.INSTANCE
        )

    override fun getVersion() = 2

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true

    override fun getIndexer() = DataIndexer<String, Collection<String>, FileContent> { fileContent ->
        val map = hashMapOf<String, Collection<String>>()
        when (fileContent.fileType) {
            JavaClassFileType.INSTANCE -> {
                val kotlinBinaryClass = ClsKotlinBinaryClassCache.getInstance().getKotlinBinaryClass(fileContent.file, fileContent.content)
                if (kotlinBinaryClass != null) {
                    val classId = kotlinBinaryClass.classId
                    map[classId.shortClassName.asString()] = listOf(classId.asFqNameString())
                }
            }
            KotlinBuiltInFileType -> {
                val builtins: KotlinMetadataStubBuilder.FileWithMetadata? = BuiltInDefinitionFile.read(fileContent.content, fileContent.file.parent)
                if (builtins is KotlinMetadataStubBuilder.FileWithMetadata.Compatible) {
                    for (classProto in builtins.classesToDecompile) {
                        val classId = builtins.nameResolver.getClassId(classProto.fqName)
                        map[classId.shortClassName.asString()] = listOf(classId.asFqNameString())
                    }
                }
            }
            KotlinFileType.INSTANCE -> {
                val ktFile = fileContent.psiFile as? KtFile ?: return@DataIndexer emptyMap()
                ktFile.acceptChildren(object : KtTreeVisitorVoid() {
                    override fun visitEnumEntry(enumEntry: KtEnumEntry) {
                        add(enumEntry.name, enumEntry.fqName?.asString())
                        super.visitEnumEntry(enumEntry)
                    }

                    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                        add(classOrObject.name, classOrObject.fqName?.asString())
                        super.visitClassOrObject(classOrObject)
                    }

                    private fun add(name: String?, fqName: String?) {
                        if (name != null && fqName != null) {
                            val fqNames = map.getOrPut(name) {
                                ArrayList()
                            } as ArrayList
                            fqNames += fqName
                        }
                    }
                })
            }
        }
        map
    }
}

object StringListExternalizer : DataExternalizer<Collection<String>> {
    private val ELEMENTS_SERIALIZER = EnumeratorStringDescriptor()

    override fun read(input: DataInput): List<String> {
        val size = input.readInt()
        return arrayListOf<String>().apply {
            repeat(size) {
                add(ELEMENTS_SERIALIZER.read(input))
            }
        }
    }

    override fun save(output: DataOutput, value: Collection<String>) {
        output.writeInt(value.size)
        value.toList().forEach { ELEMENTS_SERIALIZER.save(output, it) }
    }
}

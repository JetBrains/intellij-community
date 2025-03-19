// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.util.indexing.*
import com.intellij.util.indexing.hints.FileTypeInputFilterPredicate
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import org.jetbrains.kotlin.idea.KotlinModuleFileType
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.metadata.jvm.deserialization.ModuleMapping
import org.jetbrains.kotlin.name.ClassId
import java.io.DataInput
import java.io.DataOutput


class KotlinJvmModuleAnnotationsIndex internal constructor() : FileBasedIndexExtension<String, List<ClassId>>() {
    companion object {
        val NAME: ID<String, List<ClassId>> = ID.create(KotlinJvmModuleAnnotationsIndex::class.java.canonicalName)
    }

    private val KEY_DESCRIPTOR = KotlinModuleMappingIndex.STRING_KEY_DESCRIPTOR

    private val VALUE_EXTERNALIZER = object : DataExternalizer<List<ClassId>> {
        override fun read(input: DataInput): List<ClassId> =
            IOUtil.readStringList(input).map(ClassId::fromString)

        override fun save(out: DataOutput, value: List<ClassId>) =
            IOUtil.writeStringList(out, value.map(ClassId::asString))
    }

    override fun getName() = NAME

    override fun dependsOnFileContent() = true

    override fun getKeyDescriptor() = KEY_DESCRIPTOR

    override fun getValueExternalizer() = VALUE_EXTERNALIZER

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        FileTypeInputFilterPredicate(KotlinModuleFileType.INSTANCE)

    override fun getVersion(): Int = 1

    override fun getIndexer(): DataIndexer<String, List<ClassId>, FileContent> = DataIndexer { inputData ->
        val file = inputData.file
        try {
            val moduleMapping = ModuleMapping.loadModuleMapping(
                bytes = inputData.content,
                debugName = file.toString(),
                skipMetadataVersionCheck = false,
                isJvmPackageNameSupported = true,
                metadataVersionFromLanguageVersion = MetadataVersion.INSTANCE,
            ) {}

            if (moduleMapping !== ModuleMapping.EMPTY) {
                return@DataIndexer mapOf(file.nameWithoutExtension to moduleMapping.moduleData.annotations.map(ClassId::fromString))
            }
        } catch (e: Exception) {
            // Exceptions are already reported in KotlinModuleMappingIndex
        }
        emptyMap()
    }
}

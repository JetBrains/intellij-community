// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.lang.manifest.ManifestFileType
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.DataOutput
import java.util.*
import java.util.jar.Manifest

fun hasSomethingInPackage(indexId: ID<FqName, Void>, fqName: FqName, scope: GlobalSearchScope): Boolean {
    return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
        !FileBasedIndex.getInstance().processValues(indexId, fqName, null, { _, _ -> false }, scope)
    })
}

@ApiStatus.Internal
object FqNameKeyDescriptor : KeyDescriptor<FqName> {
    override fun save(output: DataOutput, value: FqName) = IOUtil.writeUTF(output, value.asString())
    override fun read(input: DataInput) = FqName(IOUtil.readUTF(input))
    override fun getHashCode(value: FqName) = value.asString().hashCode()
    override fun isEqual(val1: FqName?, val2: FqName?) = val1 == val2
}

abstract class KotlinFileIndexBase : ScalarIndexExtension<FqName>() {
    protected val LOG = Logger.getInstance(javaClass)

    override fun dependsOnFileContent() = true

    override fun getKeyDescriptor() = FqNameKeyDescriptor

    protected fun indexer(f: (FileContent) -> FqName?): DataIndexer<FqName, Void, FileContent> {
        return DataIndexer {
            try {
                val fqName = f(it)
                if (fqName != null) {
                    Collections.singletonMap<FqName, Void>(fqName, null)
                } else {
                    emptyMap()
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Throwable) {
                LOG.warn("Error while indexing file ${it.fileName}: ${e.message}")
                emptyMap()
            }
        }
    }
}

class KotlinClassFileIndex : KotlinFileIndexBase() {
    companion object {
        val NAME: ID<FqName, Void> = ID.create("org.jetbrains.kotlin.idea.vfilefinder.KotlinClassFileIndex")
    }

    override fun getName() = NAME

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE)

    override fun getVersion() = 3

    private val INDEXER = indexer { fileContent ->
        val headerInfo = ClsKotlinBinaryClassCache.getInstance().getKotlinBinaryClassHeaderData(fileContent.file, fileContent.content)
        if (headerInfo != null && headerInfo.metadataVersion.isCompatible()) headerInfo.classId.asSingleFqName() else null
    }
}

class KotlinStdlibIndex : KotlinFileIndexBase() {
    companion object {
        val NAME: ID<FqName, Void> = ID.create("org.jetbrains.kotlin.idea.vfilefinder.KotlinStdlibIndex")

        val KOTLIN_STDLIB_NAME: FqName = FqName("kotlin-stdlib")
        val STANDARD_LIBRARY_DEPENDENCY_NAME: FqName = FqName("kotlin-stdlib-common")

        private const val LIBRARY_NAME_MANIFEST_ATTRIBUTE = "Implementation-Title"
        private const val STDLIB_TAG_MANIFEST_ATTRIBUTE = "Kotlin-Runtime-Component"
    }

    override fun getName() = NAME

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(ManifestFileType.INSTANCE)

    override fun getVersion() = 1

    // TODO: refactor [KotlinFileIndexBase] and get rid of FqName here, it's never a proper fully qualified name, just a String wrapper
    private val INDEXER = indexer { fileContent ->
        if (fileContent.fileType is ManifestFileType) {
            val manifest = Manifest(ByteArrayInputStream(fileContent.content))
            val attributes = manifest.mainAttributes
            attributes.getValue(STDLIB_TAG_MANIFEST_ATTRIBUTE) ?: return@indexer null
            val libraryName = attributes.getValue(LIBRARY_NAME_MANIFEST_ATTRIBUTE) ?: return@indexer null
            FqName(libraryName)
        } else null
    }
}
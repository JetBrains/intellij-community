// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltInsPackageFragmentProvider
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.caches.IDEKotlinBinaryClassCache
import org.jetbrains.kotlin.idea.decompiler.builtIns.BuiltInDefinitionFile
import org.jetbrains.kotlin.idea.decompiler.builtIns.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.decompiler.js.KotlinJavaScriptMetaFileType
import org.jetbrains.kotlin.idea.klib.KlibLoadingMetadataCache
import org.jetbrains.kotlin.idea.klib.KlibMetaFileType
import org.jetbrains.kotlin.library.metadata.KlibMetadataProtoBuf
import org.jetbrains.kotlin.metadata.js.JsProtoBuf
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment
import org.jetbrains.kotlin.serialization.deserialization.getClassId
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.lang.manifest.ManifestFileType
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.DataOutput
import java.util.*
import java.util.jar.Manifest

private val FQNAME_KEY_DESCRIPTOR: KeyDescriptor<FqName> = object : KeyDescriptor<FqName> {
    override fun save(output: DataOutput, value: FqName) = IOUtil.writeUTF(output, value.asString())

    override fun read(input: DataInput) = FqName(IOUtil.readUTF(input))

    override fun getHashCode(value: FqName) = value.asString().hashCode()

    override fun isEqual(val1: FqName?, val2: FqName?) = val1 == val2
}

abstract class KotlinFileIndexBase<T>(classOfIndex: Class<T>) : ScalarIndexExtension<FqName>() {
    val KEY: ID<FqName, Void> = ID.create(classOfIndex.canonicalName)

    protected val LOG = Logger.getInstance(classOfIndex)

    override fun getName() = KEY

    override fun dependsOnFileContent() = true

    override fun getKeyDescriptor() = FQNAME_KEY_DESCRIPTOR

    protected fun indexer(f: (FileContent) -> FqName?): DataIndexer<FqName, Void, FileContent> =
        DataIndexer {
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
                LOG.warn("Error while indexing file " + it.fileName, e)
                emptyMap()
            }
        }
}

fun FileBasedIndexExtension<FqName, Void>.hasSomethingInPackage(fqName: FqName, scope: GlobalSearchScope): Boolean =
    !FileBasedIndex.getInstance().processValues(name, fqName, null, { _, _ -> false }, scope)

object KotlinPartialPackageNamesIndex : FileBasedIndexExtension<FqName, Name?>() {
    private val LOG = Logger.getInstance(KotlinPartialPackageNamesIndex::class.java)

    private object NullableNameExternalizer: DataExternalizer<Name?> {
        override fun save(out: DataOutput, value: Name?) {
            out.writeBoolean(value == null)
            if (value != null) {
                IOUtil.writeUTF(out, value.asString())
            }
        }

        override fun read(input: DataInput): Name? =
            if (input.readBoolean()) null else Name.guessByFirstCharacter(IOUtil.readUTF(input))
    }

    val KEY: ID<FqName, Name?> = ID.create(KotlinPartialPackageNamesIndex::class.java.canonicalName)

    override fun getName() = KEY

    override fun dependsOnFileContent() = true

    override fun getKeyDescriptor() = FQNAME_KEY_DESCRIPTOR

    override fun getValueExternalizer(): DataExternalizer<Name?> = NullableNameExternalizer

    override fun getInputFilter(): DefaultFileTypeSpecificInputFilter =
        DefaultFileTypeSpecificInputFilter(
            JavaClassFileType.INSTANCE,
            KotlinFileType.INSTANCE,
            KotlinJavaScriptMetaFileType
        )

    override fun getVersion() = 3

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true

    private fun FileContent.toPackageFqName(): FqName? =
        when (this.fileType) {
            KotlinFileType.INSTANCE -> this.psiFile.safeAs<KtFile>()?.packageFqName
            JavaClassFileType.INSTANCE -> IDEKotlinBinaryClassCache.getInstance()
                .getKotlinBinaryClassHeaderData(this.file, this.content)?.packageName?.let(::FqName)
            KotlinJavaScriptMetaFileType -> this.fqNameFromJsMetadata()
            else -> null
        }

    override fun getIndexer() = DataIndexer<FqName, Name?, FileContent> { fileContent ->
        try {
            val packageFqName = fileContent.toPackageFqName() ?: return@DataIndexer emptyMap<FqName, Name?>()

            generateSequence(packageFqName) {
                it.parentOrNull()
            }.filterNot { it.isRoot }.associateBy({ it.parent() }, { it.shortName() }) + mapOf(packageFqName to null)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            LOG.warn("Error while indexing file " + fileContent.fileName, e)
            emptyMap()
        }
    }
}

object KotlinClassFileIndex : KotlinFileIndexBase<KotlinClassFileIndex>(KotlinClassFileIndex::class.java) {

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE)

    override fun getVersion() = VERSION

    private const val VERSION = 3

    private val INDEXER = indexer { fileContent ->
        val headerInfo = IDEKotlinBinaryClassCache.getInstance().getKotlinBinaryClassHeaderData(fileContent.file, fileContent.content)
        if (headerInfo != null && headerInfo.metadataVersion.isCompatible()) headerInfo.classId.asSingleFqName() else null
    }
}

object KotlinJavaScriptMetaFileIndex : KotlinFileIndexBase<KotlinJavaScriptMetaFileIndex>(KotlinJavaScriptMetaFileIndex::class.java) {

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KotlinJavaScriptMetaFileType)

    override fun getVersion() = VERSION

    private const val VERSION = 4

    private val INDEXER = indexer(FileContent::fqNameFromJsMetadata)
}

private fun FileContent.fqNameFromJsMetadata(): FqName? =
    ByteArrayInputStream(content).use { stream ->
        if (JsMetadataVersion.readFrom(stream).isCompatible()) {
            FqName(JsProtoBuf.Header.parseDelimitedFrom(stream).packageFqName)
        } else null
    }

open class KotlinMetadataFileIndexBase<T>(classOfIndex: Class<T>, indexFunction: (ClassId) -> FqName) :
    KotlinFileIndexBase<T>(classOfIndex) {
    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KotlinBuiltInFileType)

    override fun getVersion() = VERSION

    private val VERSION = 1

    private val INDEXER = indexer { fileContent ->
        if (fileContent.fileType == KotlinBuiltInFileType &&
            fileContent.fileName.endsWith(MetadataPackageFragment.DOT_METADATA_FILE_EXTENSION)
        ) {
            val builtins = BuiltInDefinitionFile.read(fileContent.content, fileContent.file)
            (builtins as? BuiltInDefinitionFile)?.let { builtinDefFile ->
                val proto = builtinDefFile.proto
                proto.class_List.singleOrNull()?.let { cls ->
                    indexFunction(builtinDefFile.nameResolver.getClassId(cls.fqName))
                } ?: indexFunction(
                    ClassId(
                        builtinDefFile.packageFqName,
                        Name.identifier(fileContent.fileName.substringBeforeLast(MetadataPackageFragment.DOT_METADATA_FILE_EXTENSION))
                    )
                )
            }
        } else null
    }
}

object KotlinMetadataFileIndex : KotlinMetadataFileIndexBase<KotlinMetadataFileIndex>(
    KotlinMetadataFileIndex::class.java, ClassId::asSingleFqName
)

object KotlinMetadataFilePackageIndex : KotlinMetadataFileIndexBase<KotlinMetadataFilePackageIndex>(
    KotlinMetadataFilePackageIndex::class.java, ClassId::getPackageFqName
)

object KotlinBuiltInsMetadataIndex : KotlinFileIndexBase<KotlinBuiltInsMetadataIndex>(KotlinBuiltInsMetadataIndex::class.java) {
    override fun getIndexer() = INDEXER

    override fun getInputFilter() = FileBasedIndex.InputFilter { file -> FileTypeRegistry.getInstance().isFileOfType(file, KotlinBuiltInFileType) }

    override fun getVersion() = VERSION

    private val VERSION = 1

    private val INDEXER = indexer { fileContent ->
        if (fileContent.fileType == KotlinBuiltInFileType &&
            fileContent.fileName.endsWith(JvmBuiltInsPackageFragmentProvider.DOT_BUILTINS_METADATA_FILE_EXTENSION)) {
            val builtins = BuiltInDefinitionFile.read(fileContent.content, fileContent.file.parent)
            (builtins as? BuiltInDefinitionFile)?.packageFqName
        } else null
    }
}

object KotlinStdlibIndex : KotlinFileIndexBase<KotlinStdlibIndex>(KotlinStdlibIndex::class.java) {
    private const val LIBRARY_NAME_MANIFEST_ATTRIBUTE = "Implementation-Title"
    private const val STDLIB_TAG_MANIFEST_ATTRIBUTE = "Kotlin-Runtime-Component"
    val KOTLIN_STDLIB_NAME = FqName("kotlin-stdlib")
    val KOTLIN_STDLIB_COMMON_NAME = FqName("kotlin-stdlib-common")

    val STANDARD_LIBRARY_DEPENDENCY_NAMES = setOf(
        KOTLIN_STDLIB_COMMON_NAME,
    )

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = FileBasedIndex.InputFilter { file -> file.fileType is ManifestFileType }

    override fun getVersion() = VERSION

    private val VERSION = 1

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

object KlibMetaFileIndex : KotlinFileIndexBase<KlibMetaFileIndex>(KlibMetaFileIndex::class.java) {

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KlibMetaFileType)

    override fun getVersion() = VERSION

    // This is to express intention to index all Kotlin/Native metadata files irrespectively to file size:
    override fun getFileTypesWithSizeLimitNotApplicable() = listOf(KlibMetaFileType)

    private const val VERSION = 4

    /*todo: check version?!*/
    private val INDEXER = indexer { fileContent ->
        val fragment = KlibLoadingMetadataCache
            .getInstance().getCachedPackageFragment(fileContent.file)
        if (fragment != null)
            FqName(fragment.getExtension(KlibMetadataProtoBuf.fqName))
        else
            null
    }
}


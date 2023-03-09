// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.EnumeratorStringDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinShortClassNameFileIndex : FileBasedIndexExtension<String, Collection<String>>() {
    companion object {
        val NAME: ID<String, Collection<String>> = ID.create(KotlinShortClassNameFileIndex::class.java.canonicalName)
    }

    override fun getName(): ID<String, Collection<String>> = NAME

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): EnumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer() = StringSetExternalizer

    override fun getInputFilter(): DefaultFileTypeSpecificInputFilter =
        DefaultFileTypeSpecificInputFilter(KotlinFileType.INSTANCE)

    override fun getVersion() = 1

    override fun traceKeyHashToVirtualFileMapping(): Boolean = true

    override fun getIndexer() = DataIndexer<String, Collection<String>, FileContent> { fileContent ->
        val ktFile = fileContent.psiFile as? KtFile ?: return@DataIndexer emptyMap()
        val map = hashMapOf<String, Collection<String>>()
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
                    val fqNames = map.computeIfAbsent(name) {
                        hashSetOf()
                    } as HashSet
                    fqNames += fqName
                }
            }
        })
        map
    }
}
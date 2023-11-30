// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.index

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.editorconfig.language.filetype.EditorConfigFileType
import org.editorconfig.language.psi.impl.EditorConfigIdentifierFinderVisitor
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor
import java.io.DataInput
import java.io.DataOutput

internal val EDITOR_CONFIG_IDENTIFIER_INDEX_ID = ID.create<String, Int>("editorconfig.index.name")

internal val isIndexing = ThreadLocal<Boolean>()

internal class EditorConfigIdentifierIndex : FileBasedIndexExtension<String, Int>() {
  override fun getValueExternalizer() = object : DataExternalizer<Int> {
    override fun save(out: DataOutput, value: Int) = out.writeInt(value)
    override fun read(`in`: DataInput) = `in`.readInt()
  }

  override fun getIndexer() = DataIndexer<String, Int, FileContent> {
    val result = mutableMapOf<String, Int>()
    val visitor = object : EditorConfigIdentifierFinderVisitor() {
      override fun collectIdentifier(identifier: EditorConfigDescribableElement) {
        if (isValidReference(identifier)) return
        result[identifier.text] = identifier.textOffset
      }
    }

    isIndexing.set(true)
    it.psiFile.accept(visitor)
    isIndexing.set(false)
    result
  }

  override fun getName() = EDITOR_CONFIG_IDENTIFIER_INDEX_ID
  override fun getVersion() = 5
  override fun dependsOnFileContent() = true
  override fun getInputFilter() = editorconfigInputFilter
  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

  private val editorconfigInputFilter = DefaultFileTypeSpecificInputFilter(EditorConfigFileType)

  private fun isValidReference(identifier: EditorConfigDescribableElement): Boolean {
    if (identifier.getDescriptor(false) !is EditorConfigReferenceDescriptor) return false
    return when (val reference = identifier.reference) {
      is PsiPolyVariantReference -> reference.multiResolve(false).isNotEmpty()
      is PsiReference -> reference.resolve() != null
      else -> false
    }
  }
}

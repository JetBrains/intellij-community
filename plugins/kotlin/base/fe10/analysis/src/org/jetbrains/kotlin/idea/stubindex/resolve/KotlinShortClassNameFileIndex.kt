// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.stubindex.resolve

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterASTTokenNode
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.psi.tree.TokenSet
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.PsiDependentFileContent
import com.intellij.util.indexing.impl.CollectionDataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.text.StringSearcher
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.indices.names.readKotlinMetadataDefinition
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.serialization.deserialization.getClassId

class KotlinShortClassNameFileIndex : FileBasedIndexExtension<String, Collection<String>>() {
    companion object {
        val NAME: ID<String, Collection<String>> = ID.create(KotlinShortClassNameFileIndex::class.java.simpleName)
    }

    init {
        if (!isShortNameFilteringEnabled) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override fun getName(): ID<String, Collection<String>> = NAME

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): EnumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer() = CollectionDataExternalizer(EnumeratorStringDescriptor.INSTANCE)

    override fun getInputFilter(): DefaultFileTypeSpecificInputFilter =
      DefaultFileTypeSpecificInputFilter(
        KotlinFileType.INSTANCE,
        KotlinBuiltInFileType,
        JavaClassFileType.INSTANCE
      )

    override fun getVersion() = 4

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
          val builtins = readKotlinMetadataDefinition(fileContent)
          if (builtins != null) {
            for (classProto in builtins.classesToDecompile) {
              val classId = builtins.nameResolver.getClassId(classProto.fqName)
              map[classId.shortClassName.asString()] = listOf(classId.asFqNameString())
            }
          }
        }
        KotlinFileType.INSTANCE -> {
          val tree = (fileContent as PsiDependentFileContent).getLighterAST()
          val fqNames = mutableListOf<String>()
          val packageDirective = LightTreeUtil.firstChildOfType(tree, tree.root, KtStubElementTypes.PACKAGE_DIRECTIVE)
          if (packageDirective != null) {
            val packageName = LightTreeUtil.firstChildOfType(tree, packageDirective, PackageTokenSet.PACKAGE_REFERENCES_TOKENS)
            if (packageName != null) {
                LightTreeUtil.toFilteredString(tree, packageName, null).split(".").mapTo(fqNames) {
                    KtPsiUtil.unquoteIdentifier(it)
                }
            }
          }

          object : RecursiveLighterASTNodeWalkingVisitor(tree) {

            override fun visitNode(element: LighterASTNode) {
              if (element.tokenType == KtStubElementTypes.CLASS ||
                  element.tokenType == KtStubElementTypes.OBJECT_DECLARATION ||
                  element.tokenType == KtStubElementTypes.ENUM_ENTRY
              ) {
                val nameIdentifier = LightTreeUtil.firstChildOfType(tree, element, KtTokens.IDENTIFIER)
                if (nameIdentifier != null) {
                  val name = KtPsiUtil.unquoteIdentifier((nameIdentifier as LighterASTTokenNode).text.toString())
                  fqNames.add(name)
                  add(name, fqNames.joinToString("."))
                  super.visitNode(element)
                  return
                }
              }
              super.visitNode(element)
            }

            override fun elementFinished(element: LighterASTNode) {
              if (element.tokenType == KtStubElementTypes.CLASS ||
                  element.tokenType == KtStubElementTypes.OBJECT_DECLARATION ||
                  element.tokenType == KtStubElementTypes.ENUM_ENTRY
              ) {
                val nameIdentifier = LightTreeUtil.firstChildOfType(tree, element, KtTokens.IDENTIFIER)
                if (nameIdentifier != null) {
                  fqNames.removeLast()
                }
              }
              super.elementFinished(element)
            }

            private fun add(name: String?, fqName: String?) {
              if (name != null && fqName != null) {
                val fqNames = map.getOrPut(name) {
                  java.util.ArrayList()
                } as ArrayList
                fqNames += fqName
              }
            }
          }.visitNode(tree.root)
        }
      }
      map
    }
}

object PackageTokenSet {
    val PACKAGE_REFERENCES_TOKENS = TokenSet.create(KtStubElementTypes.DOT_QUALIFIED_EXPRESSION, KtStubElementTypes.REFERENCE_EXPRESSION)
}
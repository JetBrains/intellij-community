// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.imports

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtUserType

class ImportMapperTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_FULL_JDK

    private val javaFullClassNameIndex get() = JavaFullClassNameIndex.getInstance()
    private val kotlinFullClassNameIndex get() = KotlinFullClassNameIndex.getInstance()
    private val kotlinTypeAliasShortNameIndex get() = KotlinTypeAliasShortNameIndex.getInstance()

    private fun findInIndex(fqName: FqName, scope: GlobalSearchScope): PsiElement? =
        javaFullClassNameIndex.get(fqName.asString(), project, scope)?.firstOrNull()
            ?: kotlinFullClassNameIndex.get(fqName.asString(), project, scope).firstOrNull()

    fun test() {
        val scope = GlobalSearchScope.everythingScope(project)
        val importsMap = ImportMapper.getImport2AliasMap()
        for ((oldName, aliasFqName) in importsMap) {
            val aliases = kotlinTypeAliasShortNameIndex.get(aliasFqName.shortName().asString(), project, scope).map {
                it.getTypeReference() ?: error("Type reference is not found: ${it.text}")
            }.distinctBy { it.text }

            assertTrue("Wrong alias number for $aliasFqName: $aliases", aliases.size == 1)
            val ktUserType = aliases.single().typeElement as? KtUserType ?: error("KtUserType is not found in $aliases")
            val resolvedClass = ktUserType.referenceExpression?.mainReference?.resolve()?.navigationElement
                ?: error("Result class is not found: ${ktUserType.text}")
            val oldClass = findInIndex(oldName, scope)?.navigationElement
            assertEquals("$oldName is not equivalent to $aliasFqName", oldClass, resolvedClass)
        }
    }

    fun `test old version`() {
        assertNull(ImportMapper.findCorrespondingKotlinFqName(FqName("kotlin.jvm.Throws"), ApiVersion.KOTLIN_1_3))
    }

    fun `test new version`() {
        assertNotNull(ImportMapper.findCorrespondingKotlinFqName(FqName("kotlin.jvm.Throws"), ApiVersion.KOTLIN_1_4))
    }
}

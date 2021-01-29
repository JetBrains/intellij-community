package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtCallableDeclaration

internal class KotlinExtensionsInObjectsByReceiverTypeIndex private constructor() : KotlinExtensionsByReceiverTypeIndex() {
    override fun getKey(): StubIndexKey<String, KtCallableDeclaration> = KEY

    companion object {
        private val KEY = KotlinIndexUtil.createIndexKey(KotlinExtensionsInObjectsByReceiverTypeIndex::class.java)
        val INSTANCE = KotlinExtensionsInObjectsByReceiverTypeIndex()
    }
}
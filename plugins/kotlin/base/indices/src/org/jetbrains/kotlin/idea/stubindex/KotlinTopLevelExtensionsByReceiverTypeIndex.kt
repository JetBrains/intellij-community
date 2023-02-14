// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.kotlin.psi.KtCallableDeclaration

object KotlinTopLevelExtensionsByReceiverTypeIndex : KotlinExtensionsByReceiverTypeIndex() {
    private val KEY: StubIndexKey<String, KtCallableDeclaration> =
        StubIndexKey.createIndexKey("org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelExtensionsByReceiverTypeIndex")

    override fun getKey() = KEY

    override fun getVersion(): Int = super.getVersion() + 1

    @JvmField
    @Suppress("REDECLARATION")
    val Companion: Companion = getJavaClass<Companion>().getField("INSTANCE").get(null) as Companion

    @Suppress("REDECLARATION")
    object Companion {
        @Deprecated(
            "Use KotlinTopLevelExtensionsByReceiverTypeIndex as object instead.",
            ReplaceWith("KotlinTopLevelExtensionsByReceiverTypeIndex"),
            DeprecationLevel.ERROR
        )
        val INSTANCE: KotlinTopLevelExtensionsByReceiverTypeIndex
            get() = KotlinTopLevelExtensionsByReceiverTypeIndex

        @Deprecated(
            "Use instance method in 'KotlinExtensionsByReceiverTypeIndex' instead of the static one",
            ReplaceWith("KotlinTopLevelExtensionsByReceiverTypeIndex.INSTANCE.receiverTypeNameFromKey(key)"),
            DeprecationLevel.ERROR
        )
        fun receiverTypeNameFromKey(key: String): String = KotlinTopLevelExtensionsByReceiverTypeIndex.receiverTypeNameFromKey(key)
    }
}

private inline fun <reified T: Any> getJavaClass(): Class<T> = T::class.java
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

class KotlinTopLevelExtensionsByReceiverTypeIndex private constructor() : KotlinExtensionsByReceiverTypeIndex() {
    override fun getKey() = KEY

    override fun getVersion(): Int = super.getVersion() + 1

    companion object {
        private val KEY = KotlinIndexUtil.createIndexKey(KotlinTopLevelExtensionsByReceiverTypeIndex::class.java)

        val INSTANCE: KotlinTopLevelExtensionsByReceiverTypeIndex = KotlinTopLevelExtensionsByReceiverTypeIndex()

        @Deprecated(
            "Use instance method in 'KotlinExtensionsByReceiverTypeIndex' instead of the static one",
            ReplaceWith("KotlinTopLevelExtensionsByReceiverTypeIndex.INSTANCE.buildKey(receiverTypeName, callableName)")
        )
        fun buildKey(receiverTypeName: String, callableName: String): String = INSTANCE.buildKey(receiverTypeName, callableName)

        @Deprecated(
            "Use instance method in 'KotlinExtensionsByReceiverTypeIndex' instead of the static one",
            ReplaceWith("KotlinTopLevelExtensionsByReceiverTypeIndex.INSTANCE.receiverTypeNameFromKey(key)")
        )
        fun receiverTypeNameFromKey(key: String): String = INSTANCE.receiverTypeNameFromKey(key)

        @Deprecated(
            "Use instance method in 'KotlinExtensionsByReceiverTypeIndex' instead of the static one",
            ReplaceWith("KotlinTopLevelExtensionsByReceiverTypeIndex.INSTANCE.callableNameFromKey(key)")
        )
        fun callableNameFromKey(key: String): String = INSTANCE.callableNameFromKey(key)
    }
}

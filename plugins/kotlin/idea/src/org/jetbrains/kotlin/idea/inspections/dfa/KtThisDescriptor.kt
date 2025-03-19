// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe

class KtThisDescriptor(val descriptor: DeclarationDescriptor, private val dfType : DfType) : VariableDescriptor  {
    override fun isStable(): Boolean {
        return true
    }

    override fun isImplicitReadPossible(): Boolean {
        return true
    }

    override fun createValue(factory: DfaValueFactory, qualifier: DfaValue?): DfaValue {
        if (qualifier != null) return factory.unknown
        return factory.varFactory.createVariableValue(this)
    }

    override fun getDfType(qualifier: DfaVariableValue?): DfType = dfType

    override fun equals(other: Any?): Boolean = other is KtThisDescriptor && other.descriptor == descriptor

    override fun hashCode(): Int = descriptor.hashCode()

    override fun toString(): String = "${descriptor.fqNameUnsafe.asString()}.this"
}
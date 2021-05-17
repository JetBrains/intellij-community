package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import org.jetbrains.kotlin.idea.refactoring.move.moveMethod.type
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class KtLocalVariableDescriptor(val variable : KtCallableDeclaration) : VariableDescriptor {
    override fun isStable(): Boolean = true

    override fun getDfType(qualifier: DfaVariableValue?): DfType {
        val varType = variable.type()
        return if (varType == null) DfType.TOP else getDfType(varType)
    }

    override fun createValue(factory: DfaValueFactory, qualifier: DfaValue?): DfaValue {
        assert(qualifier == null)
        return factory.varFactory.createVariableValue(this)
    }

    override fun equals(other: Any?): Boolean {
        return other is KtLocalVariableDescriptor && other.variable == variable
    }

    override fun hashCode(): Int {
        return variable.hashCode()
    }

    override fun toString(): String {
        return variable.name ?: "<unknown>"
    }
}
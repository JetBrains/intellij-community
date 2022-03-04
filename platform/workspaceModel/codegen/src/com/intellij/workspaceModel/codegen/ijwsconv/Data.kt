package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.commaSeparated
import deft.storage.codegen.javaFullName
import deft.storage.codegen.codeTemplate
import org.jetbrains.deft.impl.fields.Field
import storage.codegen.patcher.DefType

class Data : IjWsType() {
    private fun DefType.dataFields() =
        structure.allFields
            .filter { it.open || it.hasDefault == Field.Default.none }

    override fun DefType.code(): String = """
        fun $javaFullName.toIjWs(c: IjWsBuilder): $ijWsName = 
            $ijWsName(
                ${dataFields().commaSeparated("                ") { dataFieldAssignment() }}        
            )        
    """.codeTemplate()
}
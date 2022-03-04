package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaSimpleName
import deft.storage.codegen.lines
import deft.storage.codegen.codeTemplate
import storage.codegen.patcher.DefType

class Enum : IjWsType() {
    override fun DefType.code(): String = """
        fun $javaFullName.toIjWs(c: IjWsBuilder): $ijWsName =
            when (this) {
                ${subtypes.lines("                ") { "is $javaFullName -> ${ijWsName}.${javaSimpleName}" }}
                else -> error(this)
            }        
    """.codeTemplate()
}
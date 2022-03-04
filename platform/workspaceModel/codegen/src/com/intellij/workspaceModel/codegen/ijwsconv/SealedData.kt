package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.javaFullName
import deft.storage.codegen.lines
import deft.storage.codegen.codeTemplate
import storage.codegen.patcher.DefType

class SealedData : IjWsType() {
    override fun DefType.code(): String = """
        fun $javaFullName.toIjWs(c: IjWsBuilder): $ijWsName =
            when (this) {
                ${subtypes.lines("                ") { "is $javaFullName -> toIjWs(c)" }}
                else -> error(this)
            }        
    """.codeTemplate()
}
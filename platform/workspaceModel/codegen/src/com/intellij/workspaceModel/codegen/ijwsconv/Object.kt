package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.javaFullName
import deft.storage.codegen.codeTemplate
import storage.codegen.patcher.DefType

class Object : IjWsType() {
    override fun DefType.code(): String = """
        fun $javaFullName.toIjWs(c: IjWsBuilder) = $ijWsName        
    """.codeTemplate()
}
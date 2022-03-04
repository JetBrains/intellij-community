package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.codeTemplate
import deft.storage.codegen.commaSeparated
import deft.storage.codegen.javaFullName
import deft.storage.codegen.lines
import org.jetbrains.deft.annotations.Key
import org.jetbrains.deft.intellijWs.Ij
import org.jetbrains.deft.intellijWs.IjByParent
import org.jetbrains.deft.intellijWs.IjWsId
import storage.codegen.patcher.DefType
import storage.codegen.patcher.contains
import storage.codegen.patcher.def

class Entity : IjWsType() {
    val DefType.ijWsId
        get() = def.annotations[IjWsId::class.java]

    override fun DefType.code(): String {
        val id = ijWsId
        val code0 = if (id != null) idCode(id) else ""
        return code0 + conversionCode()
    }

    private fun DefType.idCode(id: List<String>): String {
        val ijClassName = id.firstOrNull()
        return """
            fun $javaFullName.ijWsId(c: IjWsBuilder, selfCall: Boolean = false): $ijClassName {
                if (!selfCall) {
                    ensureScheduled(c)
                }
                return $ijClassName(
                    ${keyFields().commaSeparated("        ") { dataFieldAssignment() }}                       
                )
            }
    
        """.codeTemplate()
    }

    private fun DefType.keyFields() =
        structure.allFields
            .filter {
                Key::class in it.def?.annotations
            }

    val DefType.idVal: String
        get() {
            val id = ijWsId ?: return ""
            return ",\n                id = this.ijWsId(c, true)"
        }

    private fun DefType.conversionCode(): String = """
        fun $javaFullName.ensureScheduled(c: IjWsBuilder): $ijWsName? {
            return c.getOrPut(
                this, 
                Modifiable$ijWsName::class.java$idVal, 
                init = {
                    ${entityFields().lines("                    ") { entityFieldAssignment() }}
                }, afterPut = {
                    ${notEntityFields().lines("                    ") { notEntityFieldSpread() }}
                }
            )
        }

        fun $javaFullName.toIjWs(c: IjWsBuilder): $ijWsName = c.recursionGuard(ensureScheduled(c))
    """.codeTemplate()

    private fun DefType.entityFields() =
        structure.allFields
            .filter {
                (it.name != "parent") && IjByParent::class !in it.def?.annotations || Ij::class in it.def?.annotations
            }

    private fun DefType.notEntityFields() =
        structure.allFields
            .filter {
                it.name != "parent" &&
                        IjByParent::class in it.def?.annotations
            }
}
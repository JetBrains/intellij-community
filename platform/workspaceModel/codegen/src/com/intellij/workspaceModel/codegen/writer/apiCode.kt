// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package deft.storage.codegen

import deft.storage.codegen.field.defCode
import deft.storage.codegen.field.javaType
import org.jetbrains.deft.codegen.model.DefType
import org.jetbrains.deft.codegen.model.WsEntityInterface
import org.jetbrains.deft.codegen.utils.fqn
import org.jetbrains.deft.codegen.utils.lines
import org.jetbrains.deft.impl.fields.Field
import storage.codegen.field.api
import storage.codegen.field.builderApi

fun DefType.apiCode(moduleName: String) = """     
interface $javaSimpleName: $javaSuperType {
    ${structure.allFields.lines("    ") { api }}
    
    ${innerModelsCode("    ", moduleName)}${generatedApiCode()}
}
""".trimIndent()

val DefType.innerModels: Collection<DefType>
    get() = module
        .types
        .filter { it.name.startsWith("$name.") }
        .map { it as DefType }

fun DefType.innerModelsCode(indent: String, moduleName: String): String {
    val innerModels = innerModels
    if (innerModels.isEmpty()) return ""
    return innerModels.joinToString("\n\n") {
        it.apiCode(moduleName).indentRestOnly(indent)
    } + "\n\n$indent"
}

fun DefType.generatedApiCode(indent: String = "    "): String = lines(indent) {
    line("//region generated code")
    line("//@formatter:off")

    section("interface Builder: $javaFullName, ObjBuilder<$javaFullName>") {
            list(structure.allFields.filter { it.hasSetter }) {
                if (def.kind is WsEntityInterface) wsBuilderApi else builderApi
            }
    }
    line()
    section(
        head = buildString {
            append("companion object: ObjType<$javaFullName, Builder>(")
            append(fqn(ktModule.id.javaPackage, ktModule.id.objName)).append(", ")
            append(id)
            if (base != null) {
                append(", ${base.javaFullName}")
            }
            append(")")
        }
    ) {
        if (singleton) line("val instance: $javaSimpleName by lazy { _newInstance() }")
        list(structure.declaredFields) { defCode }
    }

    line("//@formatter:on")
    lineNoNl("//endregion")
}

val Field<*, *>.wsBuilderApi: String
  get() = "override var $javaName: ${type.javaType}"


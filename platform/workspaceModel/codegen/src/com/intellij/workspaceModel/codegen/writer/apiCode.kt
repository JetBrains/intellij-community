// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package deft.storage.codegen

import com.intellij.workspaceModel.storage.CodeGeneratorVersions
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import deft.storage.codegen.field.javaType
import org.jetbrains.deft.Type
import org.jetbrains.deft.codegen.ijws.classes.noDefaultValue
import org.jetbrains.deft.codegen.ijws.classes.noOptional
import org.jetbrains.deft.codegen.ijws.classes.noRefs
import org.jetbrains.deft.codegen.ijws.classes.noPersistentId
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

  line("@${GeneratedCodeApiVersion::class.fqn}(${CodeGeneratorVersions.API_VERSION})")
  val abstractSupertype = if (base?.abstract == true) base else null
  val header = when {
    abstract && abstractSupertype != null -> {
      "interface Builder<T: $javaFullName>: $javaFullName, ${abstractSupertype.name}.Builder<T>, ${
        ModifiableWorkspaceEntity::class.fqn
      }<T>, ObjBuilder<T>"
    }
    abstractSupertype != null -> {
      "interface Builder: $javaFullName, ${abstractSupertype.name}.Builder<$javaFullName>, ${
        ModifiableWorkspaceEntity::class.fqn
      }<$javaFullName>, ObjBuilder<$javaFullName>"
    }
    abstract -> "interface Builder<T: $javaFullName>: $javaFullName, ${ModifiableWorkspaceEntity::class.fqn}<T>, ObjBuilder<T>"
    else -> "interface Builder: $javaFullName, ${ModifiableWorkspaceEntity::class.fqn}<$javaFullName>, ObjBuilder<$javaFullName>"
  }

  section(header) {
    list(structure.allFields.noPersistentId()) {
      if (def.kind is WsEntityInterface) wsBuilderApi else builderApi
    }
  }

  line()
  val builderGeneric = if (abstract) "<$javaFullName>" else ""
  val companionObjectHeader = buildString {
    append("companion object: ${Type::class.fqn}<$javaFullName, Builder$builderGeneric>(")
    if (base != null) append(base.javaFullName)
    append(")")
  }
  val mandatoryFields = structure.allFields.noRefs().noOptional().noPersistentId().noDefaultValue()
  if (!mandatoryFields.isEmpty()) {
    val fields = mandatoryFields.joinToString { "${it.name}: ${it.type.javaType}" }
    section(companionObjectHeader) {
      section("operator fun invoke($fields, init: Builder$builderGeneric.() -> Unit): $javaFullName") {
        line("val builder = builder(init)")
        list(mandatoryFields) {
          "builder.$name = $name"
        }
        line("return builder")
      }
    }
  } else {
    line(companionObjectHeader)
  }
  line("//@formatter:on")
  lineNoNl("//endregion")
}

val Field<*, *>.wsBuilderApi: String
  get() = "override var $javaName: ${type.javaType}"


// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.classes

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.ProblemLocation
import com.intellij.workspaceModel.codegen.impl.CodeGeneratorVersionCalculator
import com.intellij.workspaceModel.codegen.impl.engine.ProblemReporter
import com.intellij.workspaceModel.codegen.impl.writer.*
import com.intellij.workspaceModel.codegen.impl.writer.extensions.*
import com.intellij.workspaceModel.codegen.impl.writer.fields.*

fun ObjClass<*>.implWsEntityCode(reporter: ProblemReporter): String {
  checkReferences(this@implWsEntityCode, reporter)
  if (reporter.hasErrors()) return ""

  val inheritanceModifier = when {
    openness.extendable && !openness.instantiatable -> "abstract "
    openness.extendable && openness.instantiatable -> "open "
    else -> ""
  }

  return """package ${module.implPackage}   
 
import $module.${defaultJavaBuilderName}

${implWsEntityAnnotations}
@OptIn($WorkspaceEntityInternalApi::class)
internal ${inheritanceModifier}class $javaImplName(private val dataSource: $javaDataName): $javaFullName, ${WorkspaceEntityBase}(dataSource) {

private companion object {
${allRefsFields.lines { refsConnectionIdCode }.trimEnd()}
${getLinksOfConnectionIds(this)}
}
${allFields.find { it.name == "symbolicId" }?.let { "override val symbolicId: ${it.valueType.javaType} = super.symbolicId\n" } ?: ""}
${allFields.filter { it.name !in listOf("entitySource", "symbolicId") }.lines { implWsEntityFieldCode }.trimEnd()}

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<${ConnectionId}> {
return connections
}

${implWsEntityBuilderCode()}
}
"""
}

private val ObjClass<*>.implWsEntityAnnotations: String
  get() {
    return lines {
      if (additionalAnnotations.isNotEmpty()) {
        line(additionalAnnotations)
      }
      line("@${GeneratedCodeApiVersion}(${CodeGeneratorVersionCalculator.apiVersion})")
      lineNoNl("@${GeneratedCodeImplVersion}(${CodeGeneratorVersionCalculator.implementationMajorVersion})")
    }
  }

private fun getLinksOfConnectionIds(type: ObjClass<*>): String {
  return lines {
    line(type.allRefsFields.joinToString(separator = ",",
                                         prefix = "private val connections = listOf<$ConnectionId>(",
                                         postfix = ")") { it.refsConnectionId })
  }
}

private fun checkReferences(objClass: ObjClass<*>, reporter: ProblemReporter) {
  for (refField in objClass.allRefsFields) {
    val ref = refField.valueType.getRefType()
    val declaredReferenceFromChild =
      ref.target.refsFields.filter { it.valueType.getRefType().target == refField.receiver && it != refField } + setOf(ref.target.module,
                                                                                                                       refField.receiver.module).flatMap { it.extensions }
        .filter { it.valueType.getRefType().target == refField.receiver && it.receiver == ref.target && it != refField }
    if (declaredReferenceFromChild.isEmpty()) {
      reporter.reportProblem(
        GenerationProblem("Reference should be declared at both entities. It exist at ${objClass.name}#${refField.name}, but is absent from ${ref.target.name}. Instantiatable: from ${objClass.openness.instantiatable} to ${ref.target.openness.instantiatable}",
                          GenerationProblem.Level.ERROR,
                          ProblemLocation.Property(refField))
      )
      return@checkReferences
    }
    if (declaredReferenceFromChild.size > 1) {
      reporter.reportProblem(
        GenerationProblem("""
        |More then one reference to ${objClass.name} declared at ${declaredReferenceFromChild[0].receiver.name}#${declaredReferenceFromChild[0].name}, 
        |${declaredReferenceFromChild[1].receiver.name}#${declaredReferenceFromChild[1].name}
        |""".trimMargin(),
                          GenerationProblem.Level.ERROR,
                          ProblemLocation.Property(declaredReferenceFromChild[0]))
      )
      return@checkReferences
    }
    val referencedField = declaredReferenceFromChild[0]
    if (ref.child == referencedField.valueType.getRefType().child) {
      val (childStr, fix) = if (ref.child) {
        "child" to "Probably @Parent annotation is missing from one of the properties."
      }
      else {
        "parent" to "Probably both properties are annotated with @Parent, while only one should be."
      }
      reporter.reportProblem(
        GenerationProblem("Both fields ${objClass.name}#${refField.name} and ${ref.target.name}#${referencedField.name} are marked as $childStr. $fix",
                          GenerationProblem.Level.ERROR,
                          ProblemLocation.Property(refField))
      )
    }
  }
}

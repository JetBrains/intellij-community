// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

class KtInterface(
  val module: KtObjModule,
  parentScope: KtScope,
  val nameRange: SrcRange,
  val superTypes: List<KtType>,
  private val predefinedKind: KtInterfaceKind?,
  val annotations: KtAnnotations = KtAnnotations()
) {
  val open: Boolean = annotations.flags.open
  val abstract: Boolean = annotations.flags.abstract
  val sealed: Boolean = annotations.flags.sealed
  val name: String = nameRange.text
  override fun toString(): String = name

  val scope: KtScope = KtScope(parentScope, this)
  val file: KtFile? get() = scope.file
  lateinit var body: KtBlock

  val objType: DefType? by lazy(::asObjType)
  val simpleType: DefType? by lazy(::asSimpleType)

  val kind: KtInterfaceKind? by lazy {
    if (predefinedKind != null) return@lazy predefinedKind
    if (superTypes.isEmpty()) null
    else if (superTypes.any { it.classifier == "ObjBuilder" }) null
    else if (superTypes.any { it.classifier == "Obj" }) ObjInterface
    else if (superTypes.any { it.classifier in "WorkspaceEntity" }) WsEntityInterface()
    else if (superTypes.any { it.classifier in "WorkspaceEntityWithPersistentId" }) WsEntityWithPersistentId
    else superTypes.firstNotNullOfOrNull { scope.resolve(it.classifier)?.ktInterface?.kind }
  }

  private fun asObjType(): DefType? {
    if (kind == null || kind is WsPropertyClass) return null

    var base: DefType? = null
    superTypes.forEach {
      val superType = scope.resolve(it.classifier)
      val ktInterface = superType?.ktInterface
      if (ktInterface?.kind != null) {
        base = ktInterface.objType!!
      }
    }

    var topLevelClass: KtScope = scope
    val outerNames = mutableListOf<String>()
    do {
      if (topLevelClass.isRoot) break
      outerNames.add(topLevelClass.ktInterface!!.name)
      topLevelClass = topLevelClass.parent!!
    }
    while (true)
    outerNames.reversed().joinToString(".")

    val name = outerNames.reversed().joinToString(".")
    val type = DefType(module, name, base, this)

    return type
  }

  private fun asSimpleType(): DefType? {
    if (kind == null || kind !is WsPropertyClass) return null

    var base: DefType? = null
    superTypes.forEach {
      val superType = scope.resolve(it.classifier)
      val ktInterface = superType?.ktInterface
      if (ktInterface?.kind != null) {
        base = ktInterface.simpleType
      }
    }

    var topLevelClass: KtScope = scope
    val outerNames = mutableListOf<String>()
    do {
      if (topLevelClass.isRoot) break
      outerNames.add(topLevelClass.ktInterface!!.name)
      topLevelClass = topLevelClass.parent!!
    }
    while (true)
    outerNames.reversed().joinToString(".")

    val name = outerNames.reversed().joinToString(".")
    val type = DefType(module, name, base, this)

    return type
  }

}

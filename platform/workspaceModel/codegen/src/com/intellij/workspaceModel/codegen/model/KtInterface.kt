// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.deft.model

class KtInterface(
  val module: KtObjModule,
  parentScope: KtScope,
  val nameRange: SrcRange,
  val superTypes: List<KtType>,
  val constructor: KtConstructor?,
  private val predefinedKind: KtInterfaceKind?,
  val annotations: KtAnnotations = KtAnnotations(),
  val formExternalModule: Boolean = false
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

  fun buildFields(diagnostics: Diagnostics) {
    val type = objType
    if (type != null) {
      val byName = mutableMapOf<String, MutableList<DefField>>()
      body.defs.forEach {
        byName.getOrPut(it.name) { mutableListOf() }.add(it)
      }
      byName.values.forEachIndexed { index, defFields ->
        kind?.buildField(index, mergeFieldDefs(defFields, diagnostics), scope, type, diagnostics, module.keepUnknownFields)
      }
    }
    else if (simpleType != null && constructor != null && (kind is WsData || kind is WsSealed)) {
      constructor.defs.forEach { defField ->
        kind?.buildField(1, defField, scope, simpleType!!, diagnostics, module.keepUnknownFields)
      }
    }
  }

  private fun mergeFieldDefs(defs: MutableList<DefField>, diagnostics: Diagnostics): DefField {
    check(defs.isNotEmpty())
    if (defs.size == 1) return defs.single()
    check(defs.size == 2) { "only suspend and block expected, but found: ${defs.joinToString()}" }

    val has = arrayOf(false, false)
    lateinit var suspend: DefField
    lateinit var blocking: DefField
    defs.forEach {
      if (it.receiver == null) {
        val hasI = if (it.suspend) 1 else 0
        check(!has[hasI]) { "${it.nameRange}: duplicated suspend/blocking field" }
        has[hasI] = true
        if (it.suspend) suspend = it else blocking = it
      }
      else {
        it.todoMemberExtField(diagnostics)
      }
    }

    val msg = "suspend/blocking declarations should match"
    if (suspend.type != blocking.type) diagnostics.add(suspend.nameRange, "$msg. type not matched")
    if (blocking.expr) diagnostics.add(suspend.nameRange,
                                       "blocking field cannot have expression with suspend getter")
    if (suspend.annotations.flags != blocking.annotations.flags)
      diagnostics.add(suspend.nameRange, "$msg. annotations not matched")

    return suspend
  }
}

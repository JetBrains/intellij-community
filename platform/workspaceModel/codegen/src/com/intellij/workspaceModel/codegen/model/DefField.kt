package com.intellij.workspaceModel.codegen.deft.model

import com.intellij.workspaceModel.codegen.deft.TBlob
import com.intellij.workspaceModel.codegen.deft.TRef
import com.intellij.workspaceModel.codegen.deft.*

class DefField(
  val nameRange: SrcRange,
  val name: String,
  val type: KtType?,
  val expr: Boolean,
  val getterBody: String?,
  val constructorParam: Boolean,
  val suspend: Boolean,
  val annotations: KtAnnotations,
  val receiver: KtType? = null,
  val extensionDelegateModuleName: SrcRange? = null
) {
  val open: Boolean = annotations.flags.open
  val content: Boolean = annotations.flags.content
  val relation: Boolean = annotations.flags.relation
  val final: Boolean = !annotations.flags.default && !getterBody.isNullOrEmpty()

  var id = 0

  override fun toString(): String = buildString {
    if (final) append("final ")
    if (content) append("content ")
    if (open) append("open ")
    if (relation) append("relation ")
    if (suspend) append("suspend ")
    append("def ")
    if (receiver != null) {
      append(receiver)
      append(".")
    }
    append("$name: $type")
    if (expr) append(" = ...")
    if (extensionDelegateModuleName != null) append(" by <extension in module ${extensionDelegateModuleName.text}>")
  }

  fun toMemberField(scope: KtScope, owner: DefType, diagnostics: Diagnostics, keepUnknownFields: Boolean) {
    if (type == null) {
      diagnostics.add(nameRange, "only properties with explicit type supported")
      return
    }

    if (receiver != null) {
      todoMemberExtField(diagnostics)
      return
    }

    val valueType = type.build(scope, diagnostics, annotations, keepUnknownFields) ?: return
    val field = Field(owner, name, valueType)
    configure(field)
  }

  fun todoMemberExtField(diagnostics: Diagnostics) {
    diagnostics.add(nameRange, "extension properties inside classes is not supported yet")
  }

  fun toExtField(scope: KtScope, module: KtObjModule, diagnostics: Diagnostics) {
    //        if (extensionDelegateModuleName?.text != "Obj") return
    //
    if (type == null) {
      diagnostics.add(nameRange, "only properties with explicit type supported")
      return
    }

    if (receiver == null) {
      diagnostics.add(nameRange, "<ObjModule>.extensions() property should have receiver")
      return
    }

    val resolvedReceiver = receiver.build(scope, diagnostics, keepUnknownFields = module.keepUnknownFields)
    if (resolvedReceiver !is TRef) {
      diagnostics.add(receiver.classifierRange, "Only Obj types supported as receivers")
      return
    }

    id = module.extFields.size + 1 // todo: persistent ids
    val receiverObjType = resolvedReceiver.targetObjType

    val valueType = type.build(scope, diagnostics, annotations, keepUnknownFields = module.keepUnknownFields) ?: return
    val field = ExtField(receiverObjType, name, valueType)
    module.extFields.add(field)
    configure(field)
  }

  private fun configure(field: MemberOrExtField<*, *>) {
    field.exDef = this
    field.open = open
    if (expr) {
      field.hasDefault =
        if (suspend) Field.Default.suspend
        else Field.Default.plain
      field.defaultValue = getterBody
    }
    field.constructorField = constructorParam
    field.content = content
    field.final = final
  }


  companion object : TBlob<DefField>("")
}
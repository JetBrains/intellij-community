// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.*

object KotlinPsiDeclarationRenderer {
  fun render(declaration: KtDeclaration): String? =
    when (declaration) {
      is KtClass ->
        buildString {
          if (declaration.isAnnotation()) {
            append("annotation ")
          }
          if (declaration.isInterface()) {
            append("interface")
          }
          else {
            append("class")
          }

          append(" ")
          append(declaration.name)
          declaration.typeParameterList?.parameters?.let {
            append("<")
            for ((index, ktTypeParameter) in it.withIndex()) {
              if (index != 0) append(", ")
              append(ktTypeParameter.name)
            }
            append(">")
          }
          val superTypeListEntries = declaration.superTypeListEntries
          val superClass = superTypeListEntries.filterIsInstance<KtSuperTypeCallEntry>().firstOrNull()
          if (superClass != null) {
            superClass.calleeExpression.constructorReferenceExpression?.getReferencedName()?.let {
              append(" : ")
              append(it)
            }
          }
          else if (superTypeListEntries.isNotEmpty()) {
            superTypeListEntries.first().typeReference?.referenceName()?.let {
              append(" : ")
              append(it)
            }
          }
        }
      is KtProperty ->
        buildString {
          if (declaration.isVar) {
            append("var")
          }
          else {
            append("val")
          }
          append(" ")
          declaration.receiverTypeReference?.let {
            append(it.referenceName())
            append(".")
          }
          append(declaration.name)
          declaration.typeReference?.let {
            append(": ")
            append(it.referenceName())
          }
        }
      is KtTypeParameter -> buildString {
        append("<")
        append(declaration.name)
        append(">")
      }
      is KtParameter -> buildString {
        appendKtParameter(declaration)
      }
      is KtConstructor<*> -> buildString {
        append("constructor")
        append(" ")
        append(declaration.name ?: ("`" + SpecialNames.NO_NAME_PROVIDED.asString() + "`"))
        appendValueParameters(declaration)
      }
      is KtNamedFunction -> buildString {
        if (declaration.hasModifier(KtTokens.INFIX_KEYWORD)) {
          append("infix")
          append(" ")
        }
        if (declaration.hasModifier(KtTokens.OPERATOR_KEYWORD)) {
          append("operator")
          append(" ")
        }
        append("fun")
        append(" ")
        if (declaration.hasTypeParameterListBeforeFunctionName()) {
          append("<")
          for ((index, ktTypeParameter) in declaration.typeParameters.withIndex()) {
            if (index != 0) append(", ")
            append(ktTypeParameter.name)
          }
          append(">")
          append(" ")
        }
        declaration.receiverTypeReference?.let {
          append(it.referenceName())
          append(".")
        }
        append(declaration.name)
        appendValueParameters(declaration)
        val typeReference = declaration.typeReference
        if (typeReference != null) {
          append(": ")
          append(typeReference.referenceName())
        } else if (declaration.hasBlockBody()) {
          append(": ")
          append(StandardNames.FqNames.unit.shortName())
        }
      }
      else -> null
    }

  private fun KtTypeReference.referenceName(): String? {
    val type = typeElement as? KtUserType ?: (typeElement as? KtNullableType)?.innerType as? KtUserType ?: return null
    return buildString {
      append(type.referencedName)
      if (typeElement is KtNullableType) {
        append("?")
      }
      type.typeArgumentList?.arguments?.let {
        append("<")
        for ((index: Int, typeProjection: KtTypeProjection) in it.withIndex()) {
          if (index != 0) append(", ")
          when(typeProjection.projectionKind) {
            KtProjectionKind.IN -> append("in ")
            KtProjectionKind.OUT -> append("out ")
            KtProjectionKind.STAR -> {
              append("*")
              continue
            }
            else -> {}
          }
          append(typeProjection.typeReference?.referenceName() ?: "??")
        }
        append(">")
      }
    }
  }

  private fun StringBuilder.appendKtParameter(ktParameter: KtParameter, withName: Boolean = true) {
    if (ktParameter.isVarArg) append("vararg ")
    if (withName) {
      append(ktParameter.name)
      append(": ")
    }
    append(ktParameter.typeReference?.referenceName() ?: "??")
    if (ktParameter.defaultValue != null) {
      append(" = ...")
    }
  }

  private fun StringBuilder.appendValueParameters(declaration: KtCallableDeclaration) {
    append("(")
    for ((index, ktParameter: KtParameter) in declaration.valueParameters.withIndex()) {
      if (index != 0) append(", ")
      appendKtParameter(ktParameter, withName = false)
    }
    append(")")
  }


}
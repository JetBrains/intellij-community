// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint.types

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SpacePresentation
import com.intellij.psi.*

internal fun PresentationFactory.buildRepresentation(type: PsiType): InlayPresentation {
  return type.accept(object : PsiTypeVisitor<InlayPresentation>() {
    private val visitor = this

    override fun visitClassType(classType: PsiClassType): InlayPresentation {
      val classParameters = if (classType.hasParameters()) {
        listOf(smallText("<"),
               *classType.parameters.map { it.accept(visitor) }.intersperse(smallText(", ")).toTypedArray(),
               smallText(">"))
      }
      else {
        emptyList()
      }
      val className: String = classType.className ?: classType.presentableText
      return seq(
        psiSingleReference(smallText(className)) { classType.resolve() },
        *classParameters.toTypedArray()
      )
    }

    override fun visitArrayType(arrayType: PsiArrayType): InlayPresentation {
      return seq(
        arrayType.componentType.accept(visitor),
        smallText("[]")
      )
    }

    override fun visitWildcardType(wildcardType: PsiWildcardType): InlayPresentation {
      val boundRepresentation = wildcardType.bound?.accept(visitor)
      val boundKeywordRepresentation = when {
        wildcardType.isExtends -> seq(smallText(" extends "), boundRepresentation!!)
        wildcardType.isSuper -> seq(smallText(" super "), boundRepresentation!!)
        else -> SpacePresentation(0, 0)
      }
      return seq(
        smallText("?"),
        boundKeywordRepresentation
      )
    }

    override fun visitPrimitiveType(primitiveType: PsiPrimitiveType): InlayPresentation {
      return smallText(primitiveType.name)
    }

  })
}

private fun <T> Iterable<T>.intersperse(delimiter: T): List<T> {
  val collector = mutableListOf<T>()
  for (element in this) {
    if (collector.isNotEmpty()) {
      collector.add(delimiter)
    }
    collector.add(element)
  }
  return collector
}

internal fun PresentationFactory.buildRepresentation(typeParameterList: PsiTypeParameterList): InlayPresentation {
  return typeParameterList.typeParameters.map { typeParameter ->
    val name = typeParameter.name!!
    val bound = typeParameter.extendsListTypes.map { buildRepresentation(it) }.intersperse(smallText(" & "))
    if (bound.isEmpty()) {
      smallText(name)
    }
    else {
      seq(smallText("$name extends "),
          seq(*bound.toTypedArray())
      )
    }
  }.intersperse(smallText(", "))
    .run {
      seq(smallText("<"),
          *this.toTypedArray(),
          smallText(">"))
    }
    .let { roundWithBackground(it) }
}
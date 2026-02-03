package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.SimpleTextAttributes

class PsiViewerPrimitiveNode(private val primitive: Any) : PsiViewerPropertyNode {
  companion object {
    fun isPrimitive(clazz: Class<*>): Boolean {
      return clazz == String::class.java ||
             (clazz.isPrimitive && clazz != Void.TYPE) ||
             clazz.isEnum ||
             clazz in primitiveClassWrappers()
    }
  }

  class Factory : PsiViewerPropertyNode.Factory {
    override fun isMatchingType(clazz: Class<*>): Boolean {
      return isPrimitive(clazz)
    }

    override suspend fun createNode(nodeContext: PsiViewerPropertyNode.Context, returnedValue: Any): PsiViewerPropertyNode {
      return PsiViewerPrimitiveNode(returnedValue)
    }
  }

  override val presentation: PsiViewerPropertyNode.Presentation
    get() = PsiViewerPropertyNode.Presentation {
      val colorKey = when(primitive) {
        is String, is Char -> DefaultLanguageHighlighterColors.STRING
        is Number, is Boolean -> DefaultLanguageHighlighterColors.NUMBER
        is Enum<*> -> DefaultLanguageHighlighterColors.CONSTANT
        else -> null
      }
      val color = colorKey?.let { EditorColorsManager.getInstance().globalScheme.getAttributes(it).foregroundColor }
      @Suppress("HardCodedStringLiteral") val text = if (primitive is String) {
        "\"${primitive}\""
      } else {
        primitive.toString()
      }
      it.append(text, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
    }

  override val children = PsiViewerPropertyNode.Children.NoChildren

  override val weight: Int
    get() {
      return when (primitive) {
        is Boolean -> 0
        is Number -> 1
        is Enum<*> -> 2
        is String -> 3
        else -> 4
      }
    }
}

private fun primitiveClassWrappers(): Set<Class<*>> {
  return setOf(
    java.lang.Boolean::class.java,
    java.lang.Integer::class.java,
    java.lang.Character::class.java,
    java.lang.Byte::class.java,
    java.lang.Short::class.java,
    java.lang.Double::class.java,
    java.lang.Long::class.java,
    java.lang.Float::class.java
  )
}
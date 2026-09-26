package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.dev.psiViewer.properties.tree.nodes.apiMethods.PsiViewerApiMethod
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.DarculaColors
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import java.awt.Color

private val METHOD_NAME_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor(Color(128, 0, 0), DarculaColors.RED.brighter()))

fun methodNamePresentation(methodName: String): PsiViewerPropertyNode.Presentation {
  return PsiViewerPropertyNode.Presentation {
    it.icon = AllIcons.Nodes.Function
    @Suppress("HardCodedStringLiteral")
    it.append("$methodName() =", METHOD_NAME_ATTRIBUTES)
  }
}

fun psiViewerPsiTypeAttributes(): SimpleTextAttributes {
  val color = EditorColorsManager.getInstance().globalScheme.getAttributes(DefaultLanguageHighlighterColors.CONSTANT).foregroundColor
  return SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)
}

fun methodReturnTypePresentation(returnType: PsiViewerApiMethod.ReturnType): PsiViewerPropertyNode.Presentation {
  val text = if (returnType.returnedCollectionType != null && !returnType.returnType.isArray) {
    "<${returnType.returnType.canonicalName}<${returnType.returnedCollectionType.canonicalName}>>"
  } else {
    "<${returnType.returnType.canonicalName}>"
  }
  return PsiViewerPropertyNode.Presentation {
    it.append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}

fun nullValuePresentation(): PsiViewerPropertyNode.Presentation {
  return PsiViewerPropertyNode.Presentation {
    @Suppress("HardCodedStringLiteral")
    it.append("null", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
package com.intellij.dev.psiViewer.properties.tree.nodes

import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.icons.AllIcons
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

fun methodReturnTypePresentation(returnType: Class<*>): PsiViewerPropertyNode.Presentation {
  return PsiViewerPropertyNode.Presentation {
    it.append("<${returnType.canonicalName}>", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}

fun nullValuePresentation(): PsiViewerPropertyNode.Presentation {
  return PsiViewerPropertyNode.Presentation {
    @Suppress("HardCodedStringLiteral")
    it.append("null", SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
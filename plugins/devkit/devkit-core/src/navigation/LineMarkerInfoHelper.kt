// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.navigation.DomGotoRelatedItem
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.devkit.core.icons.DevkitCoreIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.DomUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.dom.Action
import org.jetbrains.idea.devkit.dom.Component
import org.jetbrains.idea.devkit.dom.Extension
import org.jetbrains.idea.devkit.dom.ExtensionPoint
import org.jetbrains.idea.devkit.dom.Group
import org.jetbrains.idea.devkit.dom.Listeners
import org.jetbrains.idea.devkit.util.ActionCandidate
import org.jetbrains.idea.devkit.util.ComponentCandidate
import org.jetbrains.idea.devkit.util.ListenerCandidate
import org.jetbrains.idea.devkit.util.PointableCandidate
import javax.swing.Icon

internal object LineMarkerInfoHelper {

  @JvmStatic
  fun createExtensionLineMarkerInfo(targets: List<PointableCandidate>, element: PsiElement): RelatedItemLineMarkerInfo<PsiElement>? {
    return createPluginLineMarkerInfo<Extension>(
      targets, element,
      popup = DevKitBundle.message("gutter.related.navigation.choose.extension"),
      icon = DevkitCoreIcons.Gutter.Plugin,
      namer = { getExtensionPointName(it.extensionPoint) }
    )
  }

  @JvmStatic
  fun createExtensionPointLineMarkerInfo(targets: List<PointableCandidate>, element: PsiElement): RelatedItemLineMarkerInfo<PsiElement>? {
    return createPluginLineMarkerInfo<ExtensionPoint>(
      targets, element,
      popup = DevKitBundle.message("gutter.related.navigation.choose.extension.point"),
      icon = DevkitCoreIcons.Gutter.ExtensionPoint,
      namer = { getExtensionPointName(it) }
    )
  }

  @JvmStatic
  fun createListenerLineMarkerInfo(targets: List<ListenerCandidate>, element: PsiElement): RelatedItemLineMarkerInfo<PsiElement>? {
    return createPluginLineMarkerInfo<Listeners.Listener>(
      targets, element,
      popup = DevKitBundle.message("gutter.related.navigation.choose.listener"),
      icon = DevkitCoreIcons.Gutter.Plugin,
      namer = { it.topicClassName.stringValue }
    )
  }

  @JvmStatic
  fun createListenerTopicLineMarkerInfo(targets: List<ListenerCandidate>, element: PsiElement): RelatedItemLineMarkerInfo<PsiElement>? {
    return createPluginLineMarkerInfo<Listeners.Listener>(
      targets, element,
      popup = DevKitBundle.message("gutter.related.navigation.choose.listener"),
      icon = DevkitCoreIcons.Gutter.Plugin,
      namer = { it.listenerClassName.stringValue }
    )
  }

  @JvmStatic
  fun createActionLineMarkerInfo(targets: List<ActionCandidate>, element: PsiElement): RelatedItemLineMarkerInfo<PsiElement>? {
    return createPluginLineMarkerInfo(
      targets, element,
      popup = DevKitBundle.message("gutter.related.navigation.choose.action"),
      icon = DevkitCoreIcons.Gutter.Plugin,
      namer = Action::getEffectiveId
    )
  }

  @JvmStatic
  fun createActionGroupLineMarkerInfo(targets: List<ActionCandidate>, element: PsiElement): RelatedItemLineMarkerInfo<PsiElement>? {
    return createPluginLineMarkerInfo(
      targets, element,
      popup = DevKitBundle.message("gutter.related.navigation.choose.action.group"),
      icon = DevkitCoreIcons.Gutter.Plugin,
      namer = Group::getEffectiveId
    )
  }

  @JvmStatic
  fun createComponentLineMarkerInfo(targets: List<ComponentCandidate>, element: PsiElement): RelatedItemLineMarkerInfo<PsiElement>? {
    return createPluginLineMarkerInfo<Component>(
      targets, element,
      popup = DevKitBundle.message("gutter.related.navigation.choose.component"),
      icon = DevkitCoreIcons.Gutter.Plugin,
      namer = { it.implementationClass.stringValue }
    )
  }

  private fun getExtensionPointName(element: DomElement?): String {
    return (element as? ExtensionPoint)?.effectiveQualifiedName ?: "?"
  }

  private fun <T : DomElement> createPluginLineMarkerInfo(
    targets: List<PointableCandidate>,
    element: PsiElement,
    @Nls(capitalization = Nls.Capitalization.Title) popup: String,
    icon: Icon,
    namer: (T) -> @NlsSafe String?,
  ): RelatedItemLineMarkerInfo<PsiElement>? {
    return NavigationGutterIconBuilder
      .create<PointableCandidate>(icon, { listOfNotNull(it.pointer.element) }) { target ->
        val domElement = DomUtil.getDomElement(target.pointer.element)
        listOf(object : DomGotoRelatedItem(domElement, "DevKit") {
          override fun getCustomName(): String = getDomElementName(domElement, namer)

          override fun getCustomContainerName(): @Nls String? = this.element?.let { getContainerPath(it) }
        })
      }
      .setTargets(targets)
      .setPopupTitle(popup)
      .setNamer {
        val domElement = DomUtil.getDomElement(it.pointer.element)
        getDomElementName(domElement, namer)
      }
      .setTargetRenderer {
        object : PsiTargetPresentationRenderer<PsiElement>() {
          override fun getElementText(element: PsiElement): @Nls String {
            val domElement = DomUtil.getDomElement(element)
            return getDomElementName(domElement, namer)
          }

          override fun getContainerText(element: PsiElement): @Nls String = getContainerPath(element)

          override fun getIcon(element: PsiElement): Icon? {
            val domElement = DomUtil.getDomElement(element)
            checkNotNull(domElement)
            return domElement.presentation.icon ?: element.getIcon(0)
          }
        }
      }
      .setAlignment(GutterIconRenderer.Alignment.RIGHT)
      .createLineMarkerInfo(element)
  }

  private fun getContainerPath(element: PsiElement): @NlsSafe String {
    return UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(element.project, element.containingFile.virtualFile)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : DomElement> getDomElementName(domElement: DomElement?, namer: (T) -> @NlsSafe String?): @NlsSafe String {
    return namer(domElement as T).takeUnless { it.isNullOrEmpty() } ?: "?"
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.structure

import com.intellij.devkit.apiDump.lang.icons.ADIcons
import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.devkit.apiDump.lang.psi.ADFile
import com.intellij.ide.structureView.*
import com.intellij.ide.structureView.StructureViewModel.ElementInfoProvider
import com.intellij.ide.util.treeView.TreeAnchorizer
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

internal class ADFileStructureFactory : PsiStructureViewFactory {
  override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? =
    if (psiFile is ADFile) ADFileStructureBuilder(psiFile) else null
}

private class ADFileStructureBuilder(val file: ADFile) : TreeBasedStructureViewBuilder() {
  override fun createStructureViewModel(editor: Editor?): StructureViewModel =
    ADStructureViewModel(file, editor)
}

private class ADStructureViewModel(file: ADFile, editor: Editor?)
  : StructureViewModelBase(/* psiFile = */ file, /* editor = */ editor, /* root = */ ADRootNode(file)), ElementInfoProvider {

  override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
    element is ADRootNode

  override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
    element is ADClassNode
}

private abstract class ADStructureViewElement<Psi : PsiElement>(element: Psi) : StructureViewTreeElement, ItemPresentation {
  private val element: Any = TreeAnchorizer.getService().createAnchor(element)

  protected val psi: Psi?
    get() = getValue() as? Psi

  final override fun getValue(): Any? =
    TreeAnchorizer.getService().retrieveElement(element)

  final override fun getPresentation(): ItemPresentation = this

  private val navigatable: Navigatable? get() = psi as? Navigatable

  override fun navigationRequest(): NavigationRequest? =
    navigatable?.navigationRequest()

  override fun navigate(requestFocus: Boolean): Unit =
    navigatable?.navigate(requestFocus) ?: Unit

  override fun canNavigate(): Boolean =
    navigatable?.canNavigate() ?: false

  override fun canNavigateToSource(): Boolean =
    navigatable?.canNavigateToSource() ?: false
}

private class ADRootNode(file: ADFile) : ADStructureViewElement<ADFile>(file) {
  override fun getChildren(): Array<out TreeElement> {
    val classDeclarations = psi?.classDeclarations ?: emptyList()
    return classDeclarations.map {
      ADClassNode(it)
    }.toTypedArray()
  }

  override fun getPresentableText(): @NlsSafe String? =
    psi?.name

  override fun getIcon(unused: Boolean): Icon? =
    psi?.getIcon(0)
}

private class ADClassNode(classDeclaration: ADClassDeclaration) : ADStructureViewElement<ADClassDeclaration>(classDeclaration) {
  override fun getChildren(): Array<out TreeElement?> =
    TreeElement.EMPTY_ARRAY

  override fun getPresentableText(): @NlsSafe String? =
    psi?.classHeader?.typeReference?.identifierList?.lastOrNull()?.text ?: "Unknown"

  override fun getIcon(unused: Boolean): Icon? =
    psi?.let { ADIcons.getIcon(it) } ?: ADIcons.classIcon
}
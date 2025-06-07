package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

//class LookupUI(data: ComponentData) : UiComponent(data) {
//  val list: JBList get() = driver.new(JBList::class, robot, component)
//}

@Remote("com.intellij.codeInsight.lookup.LookupManager")
interface LookupManager {
  fun getInstance(project: Project): LookupManager

  fun getActiveLookup(): Lookup
}

@Remote("com.intellij.codeInsight.lookup.Lookup")
interface Lookup {
  fun getItems(): List<LookupElementRef>
}

//
@Remote("com.intellij.codeInsight.lookup.LookupElement")
interface LookupElementRef {
  fun getPsiElement(): PsiElement?
  //fun getObject(): SimpleColoredComponent
}

@Remote("com.intellij.psi.PsiElement")
interface PsiElement {
  fun getIcon(flags: Int): Icon
}

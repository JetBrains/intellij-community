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

@Remote(
  serviceInterface = "com.intellij.codeInsight.lookup.Lookup",
  value = "com.intellij.codeInsight.lookup.impl.LookupImpl")
interface Lookup {
  fun getItems(): List<LookupElementRef>
  fun isCalculating(): Boolean
  fun setCurrentItem(item: LookupElementRef)
  fun setLookupFocusDegree(lookupFocusDegree: LookupFocusDegree)
  fun refreshUi(mayCheckReused: Boolean, onExplicitAction: Boolean)
}

@Remote("com.intellij.codeInsight.lookup.LookupFocusDegree")
interface LookupFocusDegree {
  fun valueOf(name: String): LookupFocusDegree
}

@Remote("com.intellij.codeInsight.lookup.LookupElementPresentation")
interface LookupElementPresentation {
  fun getItemText(): String?
  fun getTailText(): String?
  fun getTypeText(): String?
}

//
@Remote("com.intellij.codeInsight.lookup.LookupElement")
interface LookupElementRef {
  fun getPsiElement(): PsiElement?
  fun renderElement(presentation: LookupElementPresentation)
  //fun getObject(): SimpleColoredComponent
}

@Remote("com.intellij.psi.PsiElement")
interface PsiElement {
  fun getIcon(flags: Int): Icon
}

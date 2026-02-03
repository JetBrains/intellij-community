package com.intellij.driver.sdk

import com.intellij.driver.client.Remote


object PlatformInspectionsNames {
  const val UNUSED_IMPORT: String = "UnusedImport"
  const val UNUSED: String = "unused"
}

@Remote(
  serviceInterface = "com.intellij.profile.codeInspection.InspectionProfileManager",
  value = "com.intellij.codeInspection.ex.ApplicationInspectionProfileManager"
)
interface InspectionProfileManager {
  fun getCurrentProfile(): InspectionProfileImpl
}

@Remote("com.intellij.codeInspection.ex.InspectionProfileImpl")
interface InspectionProfileImpl {
  fun getEditorAttributes(shortName: String, element: PsiElement): TextAttributesKey
}
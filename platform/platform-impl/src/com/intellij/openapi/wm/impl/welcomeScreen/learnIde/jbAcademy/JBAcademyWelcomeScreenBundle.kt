package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.JetBrainsAcademyWelcomeScreenBundle"
object JBAcademyWelcomeScreenBundle : DynamicBundle(BUNDLE) {
  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)

}
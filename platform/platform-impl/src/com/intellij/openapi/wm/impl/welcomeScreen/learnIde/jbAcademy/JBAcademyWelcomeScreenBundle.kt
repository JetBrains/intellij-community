package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.JetBrainsAcademyWelcomeScreenBundle"

internal object JBAcademyWelcomeScreenBundle {
  private val instance = DynamicBundle(JBAcademyWelcomeScreenBundle::class.java, BUNDLE)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = instance.getMessage(key, *params)
}
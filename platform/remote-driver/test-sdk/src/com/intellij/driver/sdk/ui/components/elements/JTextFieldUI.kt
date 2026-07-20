package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JTextField


fun Finder.textField(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(JTextField::class.java) }, JTextFieldUI::class.java)

fun Finder.textField(init: QueryBuilder.() -> String) = x(JTextFieldUI::class.java, init)

class JTextFieldUI(data: ComponentData) : JTextComponentUI(data)

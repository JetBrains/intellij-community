package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.JComponent

fun <T> Pair<T, BoundConfigurable>.toNamedConfigurable(): NamedConfigurable<T> {
  val (element, boundConfigurable) = this
  return object : NamedConfigurable<T>() {
    override fun getBannerSlogan(): String = "TODO"

    override fun isModified(): Boolean = boundConfigurable.isModified

    override fun getDisplayName(): String = boundConfigurable.displayName

    override fun apply() = boundConfigurable.apply()

    override fun setDisplayName(name: String) = Unit

    override fun getEditableObject(): T = element

    override fun createOptionsPanel(): JComponent = boundConfigurable.createComponent() ?: throw IllegalStateException()
  }
}
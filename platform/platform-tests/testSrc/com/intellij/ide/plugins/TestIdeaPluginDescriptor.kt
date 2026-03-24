// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.Date

abstract class TestIdeaPluginDescriptor : IdeaPluginDescriptor {
  override fun getDependencies(): List<IdeaPluginDependency> {
    throw AssertionError("unexpected call")
  }

  override fun getDescriptorPath(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getPluginId(): PluginId {
    throw AssertionError("unexpected call")
  }

  override fun getPluginClassLoader(): ClassLoader? {
    throw AssertionError("unexpected call")
  }

  override fun getPluginPath(): Path? {
    throw AssertionError("unexpected call")
  }

  override fun getDescription(): @Nls String? {
    throw AssertionError("unexpected call")
  }

  override fun getChangeNotes(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getName(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getProductCode(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getReleaseDate(): Date? {
    throw AssertionError("unexpected call")
  }

  override fun getReleaseVersion(): Int {
    throw AssertionError("unexpected call")
  }

  override fun isLicenseOptional(): Boolean {
    throw AssertionError("unexpected call")
  }

  override fun getVendor(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getVersion(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getResourceBundleBaseName(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getCategory(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getVendorEmail(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getVendorUrl(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getUrl(): String? {
    throw AssertionError("unexpected call")
  }

  override fun getSinceBuild(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  override fun getUntilBuild(): @NlsSafe String? {
    throw AssertionError("unexpected call")
  }

  @Deprecated("Deprecated in Java")
  override fun isEnabled(): Boolean {
    throw AssertionError("unexpected call")
  }
}
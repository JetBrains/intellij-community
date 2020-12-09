// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PluginHostsProcessorHelper")
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName

private val LOG = logger<UpdateOptions>()
private val PLUGIN_HOSTS_PROCESSOR_EP = ExtensionPointName<PluginHostsProcessor>("com.intellij.pluginHostsProcessor")

interface PluginHostsProcessor {
  fun processPluginRepositories(hosts: List<String>): List<String>
}

internal fun processPluginRepositories(hosts: List<String>): List<String> {
  var result = hosts
  for (provider in PLUGIN_HOSTS_PROCESSOR_EP.extensionList) {
    LOG.runAndLogException {
      result = provider.processPluginRepositories(hosts)
    }
  }
  return result
}
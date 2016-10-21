/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.ext.logback

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.resolve.GrImportContributor
import org.jetbrains.plugins.groovy.lang.resolve.Import
import org.jetbrains.plugins.groovy.lang.resolve.ImportType.STAR
import org.jetbrains.plugins.groovy.lang.resolve.ImportType.STATIC_STAR

class LogbackImportContributor : GrImportContributor {

  private val imports by lazy {
    listOf(
        Import("ch.qos.logback.classic.encoder.PatternLayoutEncoder"),
        Import("ch.qos.logback.classic.Level", STATIC_STAR)
    ) + listOf(
        "ch.qos.logback.core",
        "ch.qos.logback.core.encoder",
        "ch.qos.logback.core.read",
        "ch.qos.logback.core.rolling",
        "ch.qos.logback.core.status",
        "ch.qos.logback.classic.net"
    ).map { Import(it, STAR) }
  }

  override fun getImports(file: GroovyFile) = if (file.isLogbackConfig()) imports else emptyList()
}
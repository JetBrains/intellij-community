// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaDirectoryService
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

interface ExpectedPackageNameProvider {

  fun inferPackageName(file: GroovyFile): String?
}

class DefaultExpectedPackageNameProvider : ExpectedPackageNameProvider {

  override fun inferPackageName(file: GroovyFile): String? = file.containingDirectory?.let {
    JavaDirectoryService.getInstance().getPackage(it)?.qualifiedName
  }
}

private val EP_NAME = ExtensionPointName.create<ExpectedPackageNameProvider>("org.intellij.groovy.expectedPackageNameProvider")

fun inferExpectedPackageName(file: GroovyFile): @NlsSafe String {
  for (ext in EP_NAME.extensions) {
    val name = ext.inferPackageName(file) ?: continue
    return name
  }
  return ""
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages

import com.intellij.lang.jvm.JvmMethod
import com.intellij.model.search.SearchRequestCollector
import com.intellij.model.search.SearchRequestor
import com.intellij.openapi.application.runReadAction
import org.jetbrains.plugins.groovy.GroovyFileType.getGroovyEnabledFileTypes
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyName

class AccessorReferenceSearchRequestor : SearchRequestor {

  override fun collectSearchRequests(collector: SearchRequestCollector) {
    val parameters = collector.parameters
    val target = parameters.target as? JvmMethod ?: return
    val propertyName = runReadAction { getPropertyName(target) } ?: return
    collector.searchWord(propertyName).restrictSearchScopeTo(*getGroovyEnabledFileTypes()).search(target)
  }
}

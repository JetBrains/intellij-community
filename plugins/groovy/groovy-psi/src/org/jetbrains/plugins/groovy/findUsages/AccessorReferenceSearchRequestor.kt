// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages

import com.intellij.lang.jvm.JvmMethod
import com.intellij.model.search.SearchRequestCollector
import com.intellij.model.search.SearchRequestor
import com.intellij.openapi.application.runReadAction
import org.jetbrains.plugins.groovy.GroovyFileType.getGroovyEnabledFileTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getFieldAccessors
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyName

class AccessorReferenceSearchRequestor : SearchRequestor {

  override fun collectSearchRequests(collector: SearchRequestCollector) {
    val target = collector.parameters.target
    if (target is JvmMethod) {
      val propertyName = runReadAction { getPropertyName(target) } ?: return
      collector.searchWord(propertyName).restrictScopeTo(*getGroovyEnabledFileTypes()).search(target)
    }
    else if (target is GrField) {
      for (accessor in runReadAction { getFieldAccessors(target) }) {
        collector.searchTarget(accessor).search()
      }
    }
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator

fun chooseOverloads(candidates: List<GroovyMethodResult>, context: GrMethodComparator.Context): List<GroovyMethodResult> {
  if (candidates.size <= 1) return candidates

  val results = SmartList<GroovyMethodResult>()
  val itr = candidates.iterator()

  results.add(itr.next())

  outer@
  while (itr.hasNext()) {
    val resolveResult = itr.next()
    val iterator = results.iterator()
    while (iterator.hasNext()) {
      val otherResolveResult = iterator.next()
      val res = GrMethodComparator.compareMethods(resolveResult, otherResolveResult, context)
      if (res > 0) {
        continue@outer
      }
      else if (res < 0) {
        iterator.remove()
      }
    }
    results.add(resolveResult)
  }

  return results
}

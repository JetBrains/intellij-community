// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.tools.apiDump.API
import com.intellij.tools.apiDump.printFlags
import com.intellij.tools.apiDump.referencedFqns
import java.util.*

/**
 * @return map with an FQN as a key, and a member (class, method, field) which exposes the FQN as a value
 */
internal fun exposedApi(api: API, filter: ApiClassFilter): ExposedApi {
  val exposedThirdPartyClasses = TreeMap<String, MutableList<String>>()
  val exposedPrivateClasses = TreeMap<String, MutableList<String>>()

  fun checkExposure(fqn: String, through: () -> String) {
    val exposures = when (api.index.isPublicOrUnknown(fqn)) {
      null -> {
        if (fqn in filter) {
          return
        }
        exposedThirdPartyClasses
      }
      false -> {
        if (fqn in filter) {
          return
        }
        // A member function exposes a package-local or internal class via its return type or parameter types.
        exposedPrivateClasses
      }
      true -> {
        return
      }
    }
    exposures.getOrPut(fqn) { ArrayList() }.add(through())
  }

  for (classData in api.publicApi) {
    val className = classData.className
    for (superClassFqn in classData.supers) {
      checkExposure(fqn = superClassFqn) {
        className
      }
    }
    for (member in classData.members) {
      for (referenced in referencedFqns(member.ref.descriptor)) {
        checkExposure(fqn = referenced) {
          buildString {
            if (printFlags(member.flags, isClass = false)) {
              append(" ")
            }
            append(className)
            append("#")
            append(member.ref.name)
            append(" ")
            append(member.ref.descriptor)
          }
        }
      }
    }
  }

  return ExposedApi(
    exposedThirdPartyClasses,
    exposedPrivateClasses,
  )
}

internal data class ExposedApi(
  val exposedThirdPartyClasses: Map<String, List<String>>,
  val exposedPrivateClasses: Map<String, List<String>>,
)

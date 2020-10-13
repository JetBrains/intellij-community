// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("FacetTestUtils")
package com.intellij.facet.mock

import com.intellij.facet.FacetType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.Disposer

inline fun <T> runWithRegisteredFacetTypes(vararg types: FacetType<*, *>, action: () -> T): T {
  val disposable = Disposer.newDisposable()
  for (type in types) {
    registerFacetType(type, disposable)
  }

  try {
    return action()
  }
  finally {
    Disposer.dispose(disposable)
  }
}


fun registerFacetType(type: FacetType<*, *>, disposable: Disposable) {
  val facetTypeDisposable = Disposer.newDisposable()
  Disposer.register(disposable, Disposable {
    runWriteActionAndWait {
      Disposer.dispose(facetTypeDisposable)
    }
  })
  runWriteActionAndWait {
    FacetType.EP_NAME.point.registerExtension(type, facetTypeDisposable)
  }
}

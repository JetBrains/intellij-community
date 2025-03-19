// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.testFramework.UsefulTestCase

class GHGQLQueryLoaderTest : UsefulTestCase() {
  fun testLoadAllQueries() {
    val queries = GHGQLQueryLoader.findAllQueries()
    assertNotEmpty(queries)
    for (query in queries) {
      GHGQLQueryLoader.loadQuery(query)
    }
  }
}
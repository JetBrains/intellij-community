// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

abstract class SurroundWithSuggesterTest : FeatureSuggesterTest() {
  override val testingSuggesterId = "Surround with"

  abstract fun `testSurround one statement with IF and get suggestion`()

  abstract fun `testSurround 2 statements with IF and add '}' at the line with second statement and get suggestion`()

  abstract fun `testSurround all statements in block with IF and get suggestion`()

  abstract fun `testSurround one statement with IF in one line and get suggestion`()

  abstract fun `testSurround statements with FOR and get suggestion`()

  abstract fun `testSurround statements with WHILE and get suggestion`()

  abstract fun `testSurround 0 statements with IF and don't get suggestion`()

  abstract fun `testWrite if() but add braces in another place and don't get suggestion`()
}

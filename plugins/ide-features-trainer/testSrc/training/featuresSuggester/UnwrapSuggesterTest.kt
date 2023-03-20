// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

abstract class UnwrapSuggesterTest : FeatureSuggesterTest() {
  override val testingSuggesterId = "Unwrap"

  abstract fun `testUnwrap IF statement and get suggestion`()

  abstract fun `testUnwrap one-line IF and get suggestion`()

  abstract fun `testUnwrap IF with deleting multiline selection and get suggestion`()

  abstract fun `testUnwrap FOR and get suggestion`()

  abstract fun `testUnwrap WHILE and get suggestion`()

  abstract fun `testUnwrap commented IF and don't get suggestion`()

  abstract fun `testUnwrap IF written in string block and don't get suggestion`()
}

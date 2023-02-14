// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

abstract class FileStructureSuggesterTest : FeatureSuggesterTest() {
  override val testingSuggesterId: String = "File structure"

  abstract fun `testFind field and get suggestion`()

  abstract fun `testFind method and get suggestion`()

  abstract fun `testFind function parameter and don't get suggestion`()

  abstract fun `testFind local variable declaration and don't get suggestion`()

  abstract fun `testFind variable usage and don't get suggestion`()

  abstract fun `testFind method usage and don't get suggestion`()

  abstract fun `testFind type usage and don't get suggestion`()
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester

abstract class IntroduceVariableSuggesterTest : FeatureSuggesterTest() {
  override val testingSuggesterId: String = "Introduce variable"

  abstract fun `testIntroduce expression from IF and get suggestion`()

  abstract fun `testIntroduce full expression from method call and get suggestion`()

  abstract fun `testIntroduce part of expression from method call and get suggestion`()

  abstract fun `testIntroduce part of string expression from method call and get suggestion`()

  abstract fun `testIntroduce full expression from return statement and get suggestion`()

  abstract fun `testIntroduce expression from method body using copy and backspace and get suggestion`()
}

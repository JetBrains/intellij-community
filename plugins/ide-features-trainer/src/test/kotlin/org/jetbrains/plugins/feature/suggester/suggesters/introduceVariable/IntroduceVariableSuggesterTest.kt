package org.jetbrains.plugins.feature.suggester.suggesters.introduceVariable

import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggesterTest

abstract class IntroduceVariableSuggesterTest : FeatureSuggesterTest() {

    abstract fun `testIntroduce expression from IF and get suggestion`()

    abstract fun `testIntroduce full expression from method call and get suggestion`()

    abstract fun `testIntroduce part of expression from method call and get suggestion`()

    abstract fun `testIntroduce part of string expression from method call and get suggestion`()

    abstract fun `testIntroduce full expression from return statement and get suggestion`()

    abstract fun `testIntroduce expression from method body using copy and backspace and get suggestion`()
}

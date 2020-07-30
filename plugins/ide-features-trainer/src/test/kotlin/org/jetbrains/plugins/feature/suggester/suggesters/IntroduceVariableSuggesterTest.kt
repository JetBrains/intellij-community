package org.jetbrains.plugins.feature.suggester.suggesters

abstract class IntroduceVariableSuggesterTest : FeatureSuggesterTest() {

    abstract fun `testIntroduce expression from IF and get suggestion`()

    abstract fun `testIntroduce full expression from method call and get suggestion`()

    abstract fun `testIntroduce part of expression from method call and get suggestion`()

    abstract fun `testIntroduce part of string expression from method call and get suggestion`()

    abstract fun `testIntroduce full expression from return statement and get suggestion`()

    abstract fun `testIntroduce expression from method body using copy and backspace and get suggestion`()

    /**
     * This case must throw suggestion but not working now
     */
    abstract fun `testIntroduce part of string declaration expression and don't get suggestion`()
}
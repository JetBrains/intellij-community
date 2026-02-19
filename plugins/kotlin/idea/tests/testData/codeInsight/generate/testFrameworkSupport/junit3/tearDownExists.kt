// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$TearDown
// K2_ACTION_CLASS: org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateTestSupportActionBase$TearDown
// NOT_APPLICABLE
// CONFIGURE_LIBRARY: JUnit
import junit.framework.TestCase

class A : TestCase() {<caret>
    override fun tearDown() {
        super.tearDown()
    }
}
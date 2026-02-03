// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$SetUp
// K2_ACTION_CLASS: org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateTestSupportActionBase$SetUp
// CONFIGURE_LIBRARY: JUnit4
// TEST_FRAMEWORK: JUnit4
// NOT_APPLICABLE
import org.junit.Before

class A {<caret>
    @Before
    fun setUp() {
        throw UnsupportedOperationException()
    }
}
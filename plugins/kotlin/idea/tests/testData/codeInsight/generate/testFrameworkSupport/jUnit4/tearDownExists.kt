// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$TearDown
// CONFIGURE_LIBRARY: JUnit4
// TEST_FRAMEWORK: JUnit4
// NOT_APPLICABLE
import org.junit.After

class A {<caret>
    @After
    fun tearDown() {
        throw UnsupportedOperationException()
    }
}
// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$TearDown
// K2_ACTION_CLASS: org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateTestSupportActionBase$TearDown
// CONFIGURE_LIBRARY: JUnit5
// TEST_FRAMEWORK: JUnit5
// NOT_APPLICABLE
class A {<caret>
    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        throw UnsupportedOperationException()
    }
}
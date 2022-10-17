// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$TearDown
// CONFIGURE_LIBRARY: JUnit
// TEST_FRAMEWORK: JUnit5
class A {<caret>
    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        throw UnsupportedOperationException()
    }
}
// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$SetUp
// CONFIGURE_LIBRARY: JUnit
// TEST_FRAMEWORK: JUnit5
class A {<caret>
    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        throw UnsupportedOperationException()
    }
}
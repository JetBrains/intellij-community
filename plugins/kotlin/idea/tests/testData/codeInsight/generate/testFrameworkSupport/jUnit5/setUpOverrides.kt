// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$SetUp
// CONFIGURE_LIBRARY: JUnit
// TEST_FRAMEWORK: JUnit5
open class A {
    open fun setUp() {

    }
}

class B : A() {<caret>

}
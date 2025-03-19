// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$SetUp
// K2_ACTION_CLASS: org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinGenerateTestSupportActionBase$SetUp
// NOT_APPLICABLE
// CONFIGURE_LIBRARY: TestNG
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

@Test class A {<caret>
    @BeforeMethod
    fun setUp() {
        throw UnsupportedOperationException()
    }
}
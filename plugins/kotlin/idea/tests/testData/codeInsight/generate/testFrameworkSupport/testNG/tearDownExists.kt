// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateTestSupportActionBase$TearDown
// NOT_APPLICABLE
// CONFIGURE_LIBRARY: TestNG
import org.testng.annotations.AfterMethod
import org.testng.annotations.Test

@Test class A {<caret>
    @AfterMethod
    fun tearDown() {
        throw UnsupportedOperationException()
    }
}
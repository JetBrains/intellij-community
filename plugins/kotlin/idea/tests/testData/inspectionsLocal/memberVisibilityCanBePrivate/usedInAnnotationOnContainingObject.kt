// WITH_STDLIB
// PROBLEM: none

annotation class Annotation(val value: String)

@Annotation(Test.ID)
object Test {
    const val <caret>ID = "test"
}

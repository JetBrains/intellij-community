// "Change 'object' to 'class'" "true"
annotation class Ann

// comment
@Ann
object Foo(val s: String) : Any() {
    <caret>constructor() : this("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix
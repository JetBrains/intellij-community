// "Change 'object' to 'class'" "true"
// K2_ERROR: None of the following candidates is applicable:<br><br>constructor(s: String): Foo<br>constructor(): Foo
// K2_ERROR: Objects cannot have constructors.
// K2_ERROR: Objects cannot have constructors.
// K2_ERROR: Unresolved reference 'Foo'.
annotation class Ann

// comment
@Ann
object Foo(val s: String) : Any() {
    <caret>constructor() : this("")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeObjectToClassFix
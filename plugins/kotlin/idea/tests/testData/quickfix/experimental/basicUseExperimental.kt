// "Opt in for 'MyExperimentalAPI' on 'bar'" "true"
// PRIORITY: HIGH
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

package a.b

@RequiresOptIn
@Target(AnnotationTarget.CLASS)
annotation class MyExperimentalAPI

@MyExperimentalAPI
class Some {
    fun foo() {}
}

class Bar {
    fun bar() {
        Some().foo<caret>()
    }
}

// "Opt in for 'MyExperimentalAPI' in 'bar'" "true"
// PRIORITY: HIGH
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

package a.b

@RequiresOptIn
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class MyExperimentalAPI

@MyExperimentalAPI
class Some {
    @MyExperimentalAPI
    fun foo() {}
}

class Bar {
    fun bar() {
        Some().foo<caret>()
    }
}

// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class Service {
    context(s: String)
    fun foo(i: Int) {}
}

fun test(service: Service) {
    service.foo(1, <caret>s = "hello")
}

// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class A
class B

fun main(fn: context(A) B.(x: Int) -> Unit) {
    fn(A(), B(), /*<# x| = #>*/5)
}
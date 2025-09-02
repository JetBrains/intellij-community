// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class A
class B {
    context(a: A)
    fun member(x: Int) {}
}

context(a: A)
fun B.extension(x: Int) {}

fun main() {
    val memberRef = B::member
    memberRef(A(), B(), /*<# x| = #>*/5)

    val extensionRef = B::extension
    extensionRef(A(), B(), /*<# x| = #>*/5)
}
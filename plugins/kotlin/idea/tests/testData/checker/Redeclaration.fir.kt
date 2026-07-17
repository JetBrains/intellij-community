// !DIAGNOSTICS: -DUPLICATE_CLASS_NAMES
<error descr="[REDECLARATION]">val a : Int = 1</error>
<error descr="[REDECLARATION]">val a : Int = 1</error>

<error descr="[CONFLICTING_OVERLOADS]">fun foo()</error> {}
<error descr="[CONFLICTING_OVERLOADS]">fun foo()</error> {}

enum class EnumClass {
    FOO,
    FOO
}

class A {
    <error descr="[REDECLARATION]">val a : Int = 1</error>
    <error descr="[REDECLARATION]">val a : Int = 1</error>

    <error descr="[CONFLICTING_OVERLOADS]">fun foo()</error> {}
    <error descr="[CONFLICTING_OVERLOADS]">fun foo()</error> {}
}

object B {
    <error descr="[REDECLARATION]">class C</error>
    <error descr="[REDECLARATION]">class C</error>
}

fun <T, T> PairParam() {}
class PParam<T, T> {}

val <T, T> T.fooParam : Int get() = 1

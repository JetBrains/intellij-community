// !DIAGNOSTICS: -DUPLICATE_CLASS_NAMES
val <error descr="">a</error> : Int = 1
val <error descr="">a</error> : Int = 1

<error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public fun foo(): Unit defined in root package in file Redeclaration.kt, public fun foo(): Unit defined in root package in file Redeclaration.kt">fun foo()</error> {}
<error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public fun foo(): Unit defined in root package in file Redeclaration.kt, public fun foo(): Unit defined in root package in file Redeclaration.kt">fun foo()</error> {}

<error descr="[DUPLICATE_SERIAL_NAME_ENUM] Enum class 'EnumClass' has duplicate serial name 'FOO' in entry 'FOO'">enum class EnumClass {
    <error descr="">FOO</error>,
    <error descr="">FOO</error>
}</error>

class A {
    val <error descr="">a</error> : Int = 1
    val <error descr="">a</error> : Int = 1

    <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public final fun foo(): Unit defined in A, public final fun foo(): Unit defined in A">fun foo()</error> {}
    <error descr="[CONFLICTING_OVERLOADS] Conflicting overloads: public final fun foo(): Unit defined in A, public final fun foo(): Unit defined in A">fun foo()</error> {}
}

object B {
    class <error descr="">C</error>
    class <error descr="">C</error>
}

fun <<error descr="">T</error>, <error descr="">T</error>> PairParam() {}
class PParam<<error descr="">T</error>, <error descr="">T</error>> {}

val <<error descr=""><error descr="[TYPE_PARAMETER_OF_PROPERTY_NOT_USED_IN_RECEIVER] Type parameter of a property must be used in its receiver type">T</error></error>, <error descr="">T</error>> T.fooParam : Int get() = 1

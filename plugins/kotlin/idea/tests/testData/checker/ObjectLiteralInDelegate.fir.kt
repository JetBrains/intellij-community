// Test for KT-8187
interface A {
    fun get(x: Int)
}

class B : A by object : A {}

class C : A by (object : A {})

class D : A by 1 <error descr="[NONE_APPLICABLE]">+</error> (object : A {})

fun bar() {
    val e = object : A by <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">object</error> : A {} {}
}

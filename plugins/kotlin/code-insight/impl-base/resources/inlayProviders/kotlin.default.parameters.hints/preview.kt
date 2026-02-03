open class A {
    open fun foo(a: Int = 1) {}
}

class B : A() {
    override fun foo(a: Int/*<#  = 1 #>*/) {}
}
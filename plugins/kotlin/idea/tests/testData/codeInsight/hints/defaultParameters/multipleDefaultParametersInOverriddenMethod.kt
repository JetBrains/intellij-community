open class A {
    open fun foo(a: Int = 1, b: String = "test") {}
}

class B : A() {
    override fun foo(b: Int/*<# [multipleDefaultParametersInOverriddenMethod.kt:41] = 1 #>*/, a: String/*<# [multipleDefaultParametersInOverriddenMethod.kt:56] = "test" #>*/) {}
}
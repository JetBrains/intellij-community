// MODE: property
class A {
    companion object {
        class InA
        fun provideInA()/*<# : |[A.Companion.InA:kotlin.fqn.class]InA #>*/ = InA()
    }
}
val inA/*<# : |[A.Companion.InA:kotlin.fqn.class]A.InA #>*/ = A.provideInA()
// MODE: property
class A {
    companion object N {
        class InA
        fun provideInA()/*<# : |[A.N.InA:kotlin.fqn.class]InA #>*/ = InA()
    }
}
val inA/*<# : |[A.N.InA:kotlin.fqn.class]A.N.InA #>*/ = A.provideInA()
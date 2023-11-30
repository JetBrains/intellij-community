// MODE: property
class A {
    companion object N {
        class InA
        fun provideInA() = InA()
    }
}
val inA/*<# [:  [temp:///src/KotlinReferencesTypeHintsProvider.kt:61]A.N.InA] #>*/ = A.provideInA()
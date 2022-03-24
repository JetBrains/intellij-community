// MODE: property
class A {
    companion object {
        class InA
        fun provideInA() = InA()
    }
}
val inA<# [:  [temp:///src/KotlinReferencesTypeHintsProvider.kt:59]A.InA] #> = A.provideInA()
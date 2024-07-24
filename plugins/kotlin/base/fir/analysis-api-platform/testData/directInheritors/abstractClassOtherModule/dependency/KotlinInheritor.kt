package test

open class KotlinInheritor : Base() {
    class NestedKotlinClass

    class NestedKotlinInheritor : Base()
}

class IndirectKotlinKotlinInheritor : KotlinInheritor()

class IndirectKotlinJavaInheritor : JavaInheritor()

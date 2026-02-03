package test

open class Module1KotlinInheritor : Base() {
    class NestedModule1KotlinClass

    class NestedModule1KotlinInheritor : Base()
}

class IndirectModule1KotlinKotlinInheritor : Module1KotlinInheritor()

class IndirectModule1KotlinJavaInheritor : Module1JavaInheritor()

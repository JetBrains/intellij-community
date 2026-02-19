package test

open class Module2KotlinInheritor : Base() {
    class NestedModule2KotlinClass

    class NestedModule2KotlinInheritor : Base()
}

class IndirectModule2KotlinKotlinInheritor : Module2KotlinInheritor()

class IndirectModule2KotlinJavaInheritor : Module2JavaInheritor()

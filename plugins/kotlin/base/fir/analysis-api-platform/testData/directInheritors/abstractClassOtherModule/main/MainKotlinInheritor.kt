package test

open class MainKotlinInheritor : Base() {
    class NestedMainKotlinClass

    class NestedMainKotlinInheritor : Base()
}

class IndirectMainKotlinKotlinInheritor : MainKotlinInheritor()

class IndirectMainKotlinJavaInheritor : MainJavaInheritor()

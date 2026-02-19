package test

open class DependencyKotlinInheritor : Base() {
    class NestedDependencyKotlinClass

    class NestedDependencyKotlinInheritor : Base()
}

class IndirectDependencyKotlinKotlinInheritor : DependencyKotlinInheritor()

class IndirectDependencyKotlinJavaInheritor : DependencyJavaInheritor()

open class DerivedClass1 : SomeInterface {
    override fun foo(demo: FileStructureDemo) {
        demo.boo()
    }
}

class SecondLevelClassA : DerivedClass1() {
    override fun foo(demo: FileStructureDemo) {
        demo.foo()
    }
}

class SecondLevelClassB : DerivedClass1()
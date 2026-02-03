package override

    interface MyInterface {
        fun foo()
    }

    abstract class MyAbstractClass {
        abstract fun bar()
    }

    open class MyClass : MyInterface, MyAbstractClass() {
        override fun foo() {}
        override fun bar() {}
    }

    class MyChildClass : MyClass() {}

    <error>class MyIllegalClass</error> : MyInterface, MyAbstractClass() {}

    <error>class MyIllegalClass2</error> : MyInterface, MyAbstractClass() {
        override fun foo() {}
    }

    <error>class MyIllegalClass3</error> : MyInterface, MyAbstractClass() {
        override fun bar() {}
    }

    <error>class MyIllegalClass4</error> : MyInterface, MyAbstractClass() {
        fun <error>foo</error>() {}
        <error>override</error> fun other() {}
    }

    class MyChildClass1 : MyClass() {
        fun <error>foo</error>() {}
        override fun bar() {}
    }

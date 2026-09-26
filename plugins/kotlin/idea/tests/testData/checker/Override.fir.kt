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

    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class MyIllegalClass</error> : MyInterface, MyAbstractClass() {}

    <error descr="[ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED]">class MyIllegalClass2</error> : MyInterface, MyAbstractClass() {
        override fun foo() {}
    }

    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED]">class MyIllegalClass3</error> : MyInterface, MyAbstractClass() {
        override fun bar() {}
    }

    <error descr="[ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED]">class MyIllegalClass4</error> : MyInterface, MyAbstractClass() {
        fun <error descr="[VIRTUAL_MEMBER_HIDDEN]">foo</error>() {}
        override fun other() {}
    }

    class MyChildClass1 : MyClass() {
        fun foo() {}
        override fun bar() {}
    }

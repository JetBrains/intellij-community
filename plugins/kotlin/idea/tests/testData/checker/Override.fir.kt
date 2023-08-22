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

    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED] Class MyIllegalClass is not abstract and does not implement abstract member foo">class MyIllegalClass</error> : MyInterface, MyAbstractClass() {}

    <error descr="[ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED] Class MyIllegalClass2 is not abstract and does not implement abstract base class member bar">class MyIllegalClass2</error> : MyInterface, MyAbstractClass() {
        override fun foo() {}
    }

    <error descr="[ABSTRACT_MEMBER_NOT_IMPLEMENTED] Class MyIllegalClass3 is not abstract and does not implement abstract member foo">class MyIllegalClass3</error> : MyInterface, MyAbstractClass() {
        override fun bar() {}
    }

    <error descr="[ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED] Class MyIllegalClass4 is not abstract and does not implement abstract base class member bar">class MyIllegalClass4</error> : MyInterface, MyAbstractClass() {
        fun <error descr="[VIRTUAL_MEMBER_HIDDEN] 'foo' hides member of supertype 'MyInterface' and needs 'override' modifier">foo</error>() {}
        override fun other() {}
    }

    class MyChildClass1 : MyClass() {
        fun foo() {}
        override fun bar() {}
    }

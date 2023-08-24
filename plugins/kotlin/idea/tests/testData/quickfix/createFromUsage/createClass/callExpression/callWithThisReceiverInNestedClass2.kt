// "Create class 'Foo'" "true"

class A<T>(val n: T) {
    inner class B<U>(val m: U) {
        fun test() = this@A.<caret>Foo(2, "2")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
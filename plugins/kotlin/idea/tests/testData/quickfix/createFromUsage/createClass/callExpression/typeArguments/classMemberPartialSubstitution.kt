// "Create class 'Foo'" "true"

class B<T>(val t: T) {

}

class A<T>(val b: B<T>) {
    fun test() = B.<caret>Foo<String>(2, "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
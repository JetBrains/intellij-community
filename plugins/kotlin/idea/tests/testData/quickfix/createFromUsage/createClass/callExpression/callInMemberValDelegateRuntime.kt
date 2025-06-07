// "Create class 'Foo'" "true"
// ERROR: Class 'Foo' is not abstract and does not implement abstract member public abstract operator fun getValue(thisRef: A<T>, property: KProperty<*>): B defined in kotlin.properties.ReadOnlyProperty
// IGNORE_K2
open class B

class A<T>(val t: T) {
    val x: B by <caret>Foo(t, "")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction
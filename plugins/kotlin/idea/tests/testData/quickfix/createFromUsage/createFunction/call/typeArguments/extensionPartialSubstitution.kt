// "Create extension function 'List<T>.foo'" "true"
// WITH_STDLIB

class A<T>(val items: List<T>) {
    fun test(): Int {
        return items.<caret>foo<Int>(2, "2")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
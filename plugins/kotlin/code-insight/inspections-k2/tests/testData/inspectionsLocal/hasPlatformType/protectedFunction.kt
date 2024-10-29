// WITH_STDLIB
// FIX: Specify return type explicitly

open class My {
    protected fun foo<caret>() = java.lang.String.valueOf(3)
}
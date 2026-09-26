// FIX: Convert sealed subclass to object

sealed class Parent

<caret>class `Foo-Bar` : Parent()

fun test(): Parent {
    return `Foo-Bar`()
}

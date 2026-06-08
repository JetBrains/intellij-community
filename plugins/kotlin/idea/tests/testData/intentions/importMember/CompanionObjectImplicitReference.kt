// PRIORITY: HIGH
// INTENTION_TEXT: "Add import for 'dep.MyClass'"
// WITH_STDLIB
package test

fun foo() {
    dep.MyClass<caret>.fromCompanion()
}


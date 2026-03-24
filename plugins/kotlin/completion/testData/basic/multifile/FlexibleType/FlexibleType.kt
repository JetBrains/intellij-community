class A
class Z

fun test(nonNullable: JavaClass, nullable: JavaClass?) {
    JavaClass.test(<caret>)
}

// WITH_ORDER
// EXIST: nonNullable
// EXIST: nullable
// EXIST: null
// EXIST: { "itemText":"JavaClass", "tailText":" (<root>)" }
// EXIST: A
// EXIST: Z
// IGNORE_K1

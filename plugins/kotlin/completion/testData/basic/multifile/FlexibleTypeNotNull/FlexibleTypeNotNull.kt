class A
class Z

fun test(nonNullable: JavaClass, nullable: JavaClass?) {
    JavaClass.test(<caret>)
}

// WITH_ORDER
// EXIST: nonNullable
// EXIST: { "itemText":"JavaClass", "tailText":" (<root>)" }
// EXIST: nullable
// EXIST: A
// EXIST: Z
// EXIST: null
// IGNORE_K1
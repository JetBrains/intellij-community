// IGNORE_K1

class Foo<Target> {

    fun foo(): Targe<caret>
}


// WITH_ORDER
// EXIST: { itemText: "Target" }
// EXIST: { itemText: "Target", tailText: " (kotlin.annotation)" }
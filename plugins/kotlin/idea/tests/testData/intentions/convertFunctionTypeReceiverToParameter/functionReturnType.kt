// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'i' is never used, could be renamed to _
// AFTER-WARNING: Parameter 'i' is never used, could be renamed to _
// AFTER-WARNING: Parameter 'it' is never used, could be renamed to _
// AFTER-WARNING: Parameter 'it' is never used, could be renamed to _
open class Foo {
    open fun foo(f: Int.(Boolean) -> String): In<caret>t.(Boolean) -> String {
        return bar(f)
    }
}

class Child : Foo() {
    override fun foo(f: Int.(Boolean) -> String): Int.(Boolean) -> String {
        return bar(f)
    }
}

class Child2 : Foo() {
    override fun foo(f: Int.(Boolean) -> String): Int.(Boolean) -> String {
        return { "" }
    }
}

class Child3 : Foo() {
    override fun foo(f: Int.(Boolean) -> String): Int.(Boolean) -> String = { "" }
}

class Child4 : Foo() {
    override fun foo(f: Int.(Boolean) -> String): Int.(Boolean) -> String = { tooo(it) }
}

class Child5 : Foo() {
    override fun foo(f: Int.(Boolean) -> String): Int.(Boolean) -> String = { this.tooo(it) }
}

fun Int.tooo(b: Boolean): String = ""

fun bar(f: (Int, Boolean) -> String): Int.(Boolean) -> String {
    return f
}

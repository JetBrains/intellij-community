interface Foo

fun test(supplier: () -> Foo) {}
fun testUsage(){
    test {
        <caret>
    }
}

// ELEMENT: object
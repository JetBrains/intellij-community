import kotlin.reflect.KProperty
// PROBLEM: none
// IGNORE_K1

fun main() {
    println("test's property: " + Test("initial foo").property)
}

class Test(private <caret>val foo: String) {
    val myLocalFoo = foo

    var property by MyDelegate {
        foo // this is MyBuilder's foo
    }
}

class MyBuilder {
    val foo = "builder-local foo"
}

class MyDelegate(init: MyBuilder.() -> String) {
    private var prop: String
    init {
        prop = MyBuilder().run(init)
    }

    operator fun getValue(test: Test, property: KProperty<*>): Any {
        return prop
    }

    operator fun setValue(test: Test, property: KProperty<*>, any: Any) {
        prop = any.toString()
    }
}

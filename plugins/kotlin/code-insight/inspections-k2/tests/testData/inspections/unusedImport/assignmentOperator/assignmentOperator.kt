import test.assignment.Property
import test.assignment.assign<caret>

class MyPlugin(val myExtension: MyExtension) {
    fun test() {
        myExtension.property = "hello"
    }
}

interface MyExtension {
    val property: Property<String>
}
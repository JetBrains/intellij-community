// K2_ACTION: "Import extension function 'Property.assign'" "false"
// IGNORE_K1
import test.assignment.Property

class MyPlugin(val myExtension: MyExtension) {
    fun test() {
        myExtension.pro<caret>perty = "hello"
    }
}

interface MyExtension {
    val property: Property<String>
}

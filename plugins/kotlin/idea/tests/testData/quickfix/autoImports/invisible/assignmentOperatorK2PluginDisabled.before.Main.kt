// K2_ACTION: "Import extension function 'Property.assign'" "false"

import test.assignment.Property

class MyPlugin(val myExtension: MyExtension) {
    fun test() {
        myExtension.pro<caret>perty = "hello"
    }
}

interface MyExtension {
    val property: Property<String>
}

// K2_ACTION: "Import extension function 'Property.assign'" "true"
// COMPILER_ARGUMENTS: -Xplugin=$KOTLIN_BUNDLED$/lib/assignment-compiler-plugin.jar
// COMPILER_PLUGIN_OPTIONS: plugin:org.jetbrains.kotlin.assignment:annotation=test.assignment.SupportsKotlinAssignmentOverloading

import test.assignment.Property

class MyPlugin(val myExtension: MyExtension) {
    fun test() {
        myExtension.pro<caret>perty = "hello"
    }
}

interface MyExtension {
    val property: Property<String>
}

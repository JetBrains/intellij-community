// COMPILER_PLUGIN_OPTIONS: plugin:org.jetbrains.kotlin.assignment:annotation=assignment.SupportsKotlinAssignmentOverloading

import assignment.Property

class MyPlugin(val myExtension: MyExtension) {
    fun test() {
        myExtension.property = "hello"
    }<caret>
}

interface MyExtension {
    val property: Property<String>
}

// IGNORE_K2
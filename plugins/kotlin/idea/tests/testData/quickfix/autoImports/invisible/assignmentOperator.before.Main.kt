// "Import extension function 'Property.assign'" "true"
// ERROR: No applicable 'assign' function found for '=' overload
// ERROR: Type mismatch: inferred type is String but Property<String> was expected
// ERROR: Val cannot be reassigned
// COMPILER_PLUGIN_OPTIONS: plugin:org.jetbrains.kotlin.assignment:annotation=test.assignment.SupportsKotlinAssignmentOverloading
// IGNORE_K2

import test.assignment.Property

class MyPlugin(val myExtension: MyExtension) {
    fun test() {
        myExtension.property <caret>= "hello"
    }
}

interface MyExtension {
    val property: Property<String>
}
// "Propagate 'PropertyTypeMarker' opt-in requirement to constructor" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Add '-opt-in=PropertyTypeMarker' to module light_idea_test_case compiler arguments
// ACTION: Convert to secondary constructor
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Move to class body
// ACTION: Opt in for 'PropertyTypeMarker' in containing file 'propertyInConstructor.kt'
// ACTION: Opt in for 'PropertyTypeMarker' on constructor
// ACTION: Opt in for 'PropertyTypeMarker' on containing class 'PropertyTypeContainer'
// ACTION: Propagate 'PropertyTypeMarker' opt-in requirement to constructor
// ACTION: Propagate 'PropertyTypeMarker' opt-in requirement to containing class 'PropertyTypeContainer'

@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

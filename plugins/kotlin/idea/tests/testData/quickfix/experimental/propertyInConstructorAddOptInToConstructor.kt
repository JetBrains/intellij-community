// "Opt in for 'PropertyTypeMarker' on constructor" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
// ACTION: Opt in for 'PropertyTypeMarker' on containing class 'PropertyTypeMarker'

@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

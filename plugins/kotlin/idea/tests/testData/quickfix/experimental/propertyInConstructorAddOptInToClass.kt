// "Opt in for 'PropertyTypeMarker' on containing class 'PropertyTypeContainer'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
// ACTION: Opt in for 'PropertyTypeMarker' on the constructor

@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

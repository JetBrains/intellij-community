// "Add '@PropertyTypeMarker' annotation to 'PropertyTypeContainer'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Add '@PropertyTypeMarker' annotation to containing class 'PropertyTypeContainer'

@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

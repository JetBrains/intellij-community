// "Opt-in for 'PropertyTypeMarker::class' on constructor" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
// ACTION: Opt-in for 'PropertyTypeMarker::class' on containing class 'PropertyTypeMarker'

@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

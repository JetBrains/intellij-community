// "Opt in for 'PropertyTypeMarker' on containing class 'PropertyTypeContainer'" "true"
// PRIORITY: HIGH
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Add full qualifier
// ACTION: Convert to secondary constructor
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Move to class body
// ACTION: Opt in for 'PropertyTypeMarker' in containing file 'propertyInConstructorAddOptInToClass.kts'
// ACTION: Opt in for 'PropertyTypeMarker' in module 'light_idea_test_case'
// ACTION: Opt in for 'PropertyTypeMarker' on constructor
// ACTION: Opt in for 'PropertyTypeMarker' on containing class 'PropertyTypeContainer'
// ACTION: Propagate 'PropertyTypeMarker' opt-in requirement to constructor
// ACTION: Propagate 'PropertyTypeMarker' opt-in requirement to containing class 'PropertyTypeContainer'

@RequiresOptIn
annotation class PropertyTypeMarker

@PropertyTypeMarker
class PropertyTypeMarked

class PropertyTypeContainer(val subject: Property<caret>TypeMarked)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
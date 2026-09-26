// "Add annotation target" "false"
// ACTION: Create test
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// WITH_STDLIB
// DISABLE_ERRORS
class Foo<@MyExperimentalAPI<caret> T> {}

@RequiresOptIn
@Target(AnnotationTarget.FIELD)
annotation class MyExperimentalAPI
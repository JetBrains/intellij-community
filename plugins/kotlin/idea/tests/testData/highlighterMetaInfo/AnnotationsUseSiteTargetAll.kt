// ALLOW_ERRORS
// HIGHLIGHT_SEVERITY: SYMBOL_TYPE_SEVERITY
// HIGHLIGHTER_ATTRIBUTES_KEY
// COMPILER_ARGUMENTS: -Xannotation-target-all

@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER
)
internal
annotation
class
Anno

class
User2(
    @all:Anno
    val email:
    String,

    @get:Anno
    val password:
    String,
)


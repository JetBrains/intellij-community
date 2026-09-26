// DISABLE_ERRORS
@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class Ann

expect val foo: Any?
    @Ann get

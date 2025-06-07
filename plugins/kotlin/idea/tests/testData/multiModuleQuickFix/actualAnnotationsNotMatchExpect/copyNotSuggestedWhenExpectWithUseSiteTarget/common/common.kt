// DISABLE_ERRORS
annotation class Ann

// Not supported scenario because of use-site target
@get:Ann
expect val foo: Any?

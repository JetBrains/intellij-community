// LANGUAGE_VERSION: 1.4
// DISABLE_ERRORS

interface Foo

annotation class Ann

class E : <caret>@field:Ann @get:Ann @set:Ann @setparam:Ann Foo

interface G : @Ann Foo
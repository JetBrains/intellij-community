data class Foo private constructor(val foo: String)
class Foo private constructor(val foo: String)
data class Foo2(val foo: String)

@ConsistentCopyVisibility
data class Foo3 private constructor(val foo: String)

@ExposedCopyVisibility
data class Foo3 private constructor(val foo: String)

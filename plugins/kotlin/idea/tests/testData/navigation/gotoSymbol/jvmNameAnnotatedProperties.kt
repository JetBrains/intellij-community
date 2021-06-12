package foo.bar

@get:JvmName("renamedBarSetter")
@set:JvmName("renamedBarGetter")
var bar: String = ""

var foo: String = ""
    @JvmName("renamedFooGetter")
    get() = field + "foo"
    @JvmName("renamedFooSetter")
    set(value) {
        field = value + "bar"
    }

// SEARCH_TEXT: renamed
// REF: @JvmName("renamedFooGetter")
// REF: @JvmName("renamedFooSetter")
// REF: @get:JvmName("renamedBarSetter")
// REF: @set:JvmName("renamedBarGetter")


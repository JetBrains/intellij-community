package test.library

open class LibraryBase {
    context(s: String)
    open fun foo() {}
}

interface LibraryInterface {
    context(s: String)
    fun foo()
}

open class LibraryProperty {
    context(s: String)
    open val value: Int
        get() = s.length
}

// IGNORE_K1
interface Foo<TSubject>

open class Bar<TSubject> {
    fun x() {
        val foo = object : Foo<T<caret>
    }
}

// EXIST: TSubject

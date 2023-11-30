// IGNORE_K1
package test

@Deprecated("hidden", level = DeprecationLevel.HIDDEN)
class DeprecatedHiddenClassFromSameFile

@Deprecated("error", level = DeprecationLevel.ERROR)
class DeprecatedErrorNotHiddenClassFromSameFile

fun test(a: Any) {
    when(a) {
        <caret>
    }
}

// ABSENT: is DeprecatedHiddenClass
// ABSENT: is DeprecatedHiddenClassFromSameFile
// EXIST: is NotHiddenClass
// EXIST: is DeprecatedErrorNotHiddenClassFromSameFile
// EXIST: is DeprecatedErrorNotHiddenClass
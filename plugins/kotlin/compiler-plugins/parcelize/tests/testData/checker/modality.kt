package test

import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Parcelize
open class Open(val foo: String) : Parcelable

@Parcelize
class Final(val foo: String) : Parcelable

@Parcelize
<error descr="[PARCELABLE_SHOULD_BE_INSTANTIABLE]">abstract</error> class Abstract(val foo: String) : Parcelable

@Parcelize
sealed class Sealed(val foo: String) : Parcelable {
    class X : Sealed("")
}

class Outer {
    @Parcelize
    <error descr="[PARCELABLE_CANT_BE_INNER_CLASS]">inner</error> class Inner(val foo: String) : Parcelable
}

fun foo() {
    @Parcelize
    <error descr="[PARCELABLE_CANT_BE_LOCAL_CLASS]">object</error> : Parcelable {}

    @Parcelize
    class <error descr="[NO_PARCELABLE_SUPERTYPE]"><error descr="[PARCELABLE_CANT_BE_LOCAL_CLASS]">Local</error></error> {}
}

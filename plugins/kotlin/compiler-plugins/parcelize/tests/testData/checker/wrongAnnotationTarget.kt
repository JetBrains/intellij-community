package test

import kotlinx.parcelize.Parcelize
import android.os.Parcelable

@Parcelize
interface <error descr="[PARCELABLE_SHOULD_BE_CLASS]">Intf</error> : Parcelable

@Parcelize
object <error descr="[NO_PARCELABLE_SUPERTYPE]">Obj</error>

class A {
    @Parcelize
    companion <error descr="[NO_PARCELABLE_SUPERTYPE]">object</error> {
        fun foo() {}
    }
}

@Parcelize
enum class <error descr="[NO_PARCELABLE_SUPERTYPE]">Enum</error> {
    WHITE, BLACK
}

@Parcelize
annotation class <error descr="[PARCELABLE_SHOULD_BE_CLASS]">Anno</error>

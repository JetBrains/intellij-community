// WITH_STDLIB
package test

import kotlinx.parcelize.*
import android.os.Parcelable

@Parcelize
object A : Parcelable {
    @IgnoredOnParcel
    var a: String = ""

    @IgnoredOnParcel
    val b: String = ""

    val <warning descr="[PROPERTY_WONT_BE_SERIALIZED]">secondName</warning>: String = ""

    val <warning descr="[PROPERTY_WONT_BE_SERIALIZED]">delegated</warning> by lazy { "" }

    lateinit var <warning descr="[PROPERTY_WONT_BE_SERIALIZED]">lateinit</warning>: String

    val customGetter: String
        get() = ""

    var customSetter: String
        get() = ""
        set(<warning descr="[UNUSED_PARAMETER]">v</warning>) {}
}
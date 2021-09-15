// WITH_RUNTIME
package test

import kotlinx.parcelize.*
import android.os.Parcelable

@Parcelize
object A : Parcelable {
    @IgnoredOnParcel
    var a: String = ""

    @IgnoredOnParcel
    val b: String = ""

    val <warning descr="[PROPERTY_WONT_BE_SERIALIZED] Property would not be serialized into a 'Parcel'. Add '@IgnoredOnParcel' annotation to remove the warning">secondName</warning>: String = ""

    val <warning descr="[PROPERTY_WONT_BE_SERIALIZED] Property would not be serialized into a 'Parcel'. Add '@IgnoredOnParcel' annotation to remove the warning">delegated</warning> by lazy { "" }

    lateinit var <warning descr="[PROPERTY_WONT_BE_SERIALIZED] Property would not be serialized into a 'Parcel'. Add '@IgnoredOnParcel' annotation to remove the warning">lateinit</warning>: String

    val customGetter: String
        get() = ""

    var customSetter: String
        get() = ""
        set(<warning descr="[UNUSED_PARAMETER] Parameter 'v' is never used">v</warning>) {}
}
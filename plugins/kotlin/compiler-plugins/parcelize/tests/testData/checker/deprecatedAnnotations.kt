// WITH_STDLIB
package test

import kotlinx.android.parcel.*
import android.os.Parcel
import android.os.Parcelable

object Parceler1 : Parceler<String> {
    override fun create(parcel: Parcel) = parcel.readInt().toString()

    override fun String.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(length)
    }
}

object Parceler2 : Parceler<List<String>> {
    override fun create(parcel: Parcel) = listOf(parcel.readString()!!)

    override fun List<String>.write(parcel: Parcel, flags: Int) {
        parcel.writeString(this.joinToString(","))
    }
}

<warning descr="[DEPRECATED_ANNOTATION]">@Parcelize</warning>
<error descr="[FORBIDDEN_DEPRECATED_ANNOTATION]">@TypeParceler<String, <error descr="[UPPER_BOUND_VIOLATED]">Parceler2</error>></error>
data class Test(
    val a: String,
    val b: <error descr="[FORBIDDEN_DEPRECATED_ANNOTATION]">@WriteWith<Parceler1></error> String,
    val c: <error descr="[FORBIDDEN_DEPRECATED_ANNOTATION]">@WriteWith<Parceler2></error> List<<error descr="[FORBIDDEN_DEPRECATED_ANNOTATION]">@WriteWith<Parceler1></error> String>
) : Parcelable {
    <warning descr="[DEPRECATED_ANNOTATION]">@IgnoredOnParcel</warning>
    val x by lazy { "foo" }
}

interface ParcelerForUser: Parceler<User>

<warning descr="[DEPRECATED_ANNOTATION]">@Parcelize</warning>
class User(val name: String) : Parcelable {
    private companion <error descr="[DEPRECATED_PARCELER]">object</error> : ParcelerForUser {
        override fun User.write(parcel: Parcel, flags: Int) {
            parcel.writeString(name)
        }

        override fun create(parcel: Parcel) = User(parcel.readString()!!)
    }
}
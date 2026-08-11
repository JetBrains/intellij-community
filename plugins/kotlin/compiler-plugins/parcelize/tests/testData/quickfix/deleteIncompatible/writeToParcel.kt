// "Remove custom 'writeToParcel()' function" "true"
// WITH_STDLIB
// ERROR: 'CREATOR' definition is not allowed. Use 'Parceler' companion object instead
// K2_ERROR: CREATOR_DEFINITION_IS_NOT_ALLOWED
// K2_ERROR: OVERRIDING_WRITE_TO_PARCEL_IS_NOT_ALLOWED
// K2_AFTER_ERROR: CREATOR_DEFINITION_IS_NOT_ALLOWED

package com.myapp.activity

import android.os.*
import kotlinx.parcelize.Parcelize

@Parcelize
class Foo(val a: String) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString()) {
    }

    <caret>override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(a)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Foo> {
        override fun createFromParcel(parcel: Parcel): Foo {
            return Foo(parcel)
        }

        override fun newArray(size: Int): Array<Foo?> {
            return arrayOfNulls(size)
        }
    }

}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelRemoveCustomWriteToParcel
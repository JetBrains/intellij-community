// "Add empty primary constructor" "true"
// WITH_STDLIB

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class <caret>Test : Parcelable {
    constructor(a: Int)
}
// "Remove 'inner' modifier" "true"
// WITH_STDLIB
// K2_ERROR: 'Parcelable' cannot be an inner class.

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

class Foo {
    @Parcelize
    <caret>inner class Bar : Parcelable
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
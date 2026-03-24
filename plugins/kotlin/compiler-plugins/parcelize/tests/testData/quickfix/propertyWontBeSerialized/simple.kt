// "Add '@IgnoredOnParcel' annotation" "true"
// WITH_STDLIB

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class Test : Parcelable {
    val <caret>a = 5
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelizeAddIgnoreOnParcelAnnotationQuickFix
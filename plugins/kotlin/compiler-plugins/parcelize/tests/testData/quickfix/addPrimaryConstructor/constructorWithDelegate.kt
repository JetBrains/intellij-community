// "Add empty primary constructor" "true"
// WITH_STDLIB

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class <caret>Test : Parcelable {
    constructor(s: String)
    constructor(s: String, i: Int) : this(s)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelizeAddPrimaryConstructorQuickFix
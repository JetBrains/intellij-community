// "Add empty primary constructor" "true"
// WITH_STDLIB
// K2_ERROR: PARCELABLE_SHOULD_HAVE_PRIMARY_CONSTRUCTOR

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class <caret>Test : Parcelable {
    constructor(a: Int)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelizeAddPrimaryConstructorQuickFix
// "Add empty primary constructor" "true"
// WITH_STDLIB
// K2_ERROR: 'Parcelable' must have a primary constructor.

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class <caret>Test : Parcelable {
    constructor(a: Int)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelizeAddPrimaryConstructorQuickFix
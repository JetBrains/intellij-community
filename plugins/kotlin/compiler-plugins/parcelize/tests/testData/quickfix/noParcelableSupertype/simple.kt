// "Add 'Parcelable' supertype" "true"
// WITH_STDLIB
// K2_ERROR: No 'Parcelable' supertype.

package com.myapp.activity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class <caret>Test(val s: String)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelizeAddSupertypeQuickFix
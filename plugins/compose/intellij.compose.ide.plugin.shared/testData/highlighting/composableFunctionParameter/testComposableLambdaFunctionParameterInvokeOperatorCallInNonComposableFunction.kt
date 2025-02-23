package test

import androidx.compose.runtime.Composable

fun context(composableLambda: @Composable () -> Unit) {
  composableLambda<caret>()
}
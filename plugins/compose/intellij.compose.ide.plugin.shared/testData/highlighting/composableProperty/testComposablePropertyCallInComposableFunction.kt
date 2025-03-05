package test

import androidx.compose.runtime.Composable

@get:Composable
val myProp: String
  get() = ""

@Composable
fun context() {
  myPr<caret>op
}

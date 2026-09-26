package test

import androidx.compose.runtime.Composable

class AClass {
  @get:Composable
  val myProp: String
    get() = ""
}

@Composable
fun context() {
  AClass().myPr<caret>op
}

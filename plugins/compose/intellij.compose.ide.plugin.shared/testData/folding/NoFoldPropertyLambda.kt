package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

val annotatedLambda = <fold text='{...}'>{
  Modifier
    .adjust()
    .adjust()
}</fold>

val explicitLambda: () -> Unit = <fold text='{...}'>{
  val m = Modifier
    .padding(8.dp)
    .fillMaxWidth()
}</fold>


val getter: Modifier
  get()<fold text='{...}'>{
    return Modifier
      .adjust()
      .adjust()
  }</fold>

val standardLambda = <fold text='{...}'>{
  val m = Modifier
    .adjust()
    .adjust()
    .adjust()
}</fold>

val explicitLambdaIfElse: () -> Unit = if (true) <fold text='{...}'>{
  <fold text='{...}'>{
    Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold> else <fold text='{...}'>{
  <fold text='{...}'>{
    Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold>
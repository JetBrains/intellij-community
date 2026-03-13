package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun MyFun() <fold text='{...}'>{
  val a = <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()</fold>

  val b = <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()
    .adjust()</fold>
}</fold>
package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun MyFun() <fold text='{...}'>{
  val folding = <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()
    .adjust()</fold>
}</fold>

@Composable
fun MyFunWithLongChain() <fold text='{...}'>{
  val folding = <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()
    .adjust()
    .adjust()
    .adjust()</fold>
}</fold>

@Composable
fun MyFunWithComments() <fold text='{...}'>{
  val folding = <fold text='Modifier.(...)'>Modifier
    .adjust()
    // comment
    .adjust()
    .adjust()
    // comment
    .adjust()
    .adjust()</fold>
}</fold>

package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

inline fun <T> myRun(block: () -> T): T = block()

@Composable
fun InlineRunFolds() <fold text='{...}'>{
  myRun <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
    }</fold>
}</fold>

@Composable
fun NestedInlineFolds() <fold text='{...}'>{
  myRun <fold text='{...}'>{
    myRun <fold text='{...}'>{
      <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
      }</fold>
    }</fold>
}</fold>
package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

val nonComposableAnnotatedLambda = <fold text='{...}'>{
  @Composable <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>

    val internalNonComposableAnnotatedLambda = <fold text='{...}'>{
      val mod = Modifier
       .adjust()
       .adjust()

      val composableLambda = @Composable <fold text='{...}'>{
       <fold text='Modifier.(...)'>Modifier
         .adjust()
         .adjust()</fold>
      }</fold>
    }</fold>
  }</fold>
}</fold>
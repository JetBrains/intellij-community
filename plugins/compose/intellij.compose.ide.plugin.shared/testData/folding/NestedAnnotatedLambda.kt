package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

val annotatedLambda = @Composable <fold text='{...}'>{
  val basicModifier = <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()</fold>

  val innerAnnotatedLambda = @Composable <fold text='{...}'>{
    val m = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>

  val explicitUnit: @Composable () -> Unit = <fold text='{...}'>{
    val m = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>

  @Composable
  fun ComposableFunction() <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()
      .adjust()</fold>
  }</fold>
}</fold>
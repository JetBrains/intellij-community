package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

val blockGetter: Modifier
  @Composable get() <fold text='{...}'>{
    return <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>

val expressionGetter: Modifier
  @Composable get() = <fold text='Modifier.(...)'>Modifier
    .adjust()
    .adjust()</fold>

val intProperty: Int
  @Composable get() <fold text='{...}'>{
    val unused = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
    return 42
}</fold>

val expressionProperty: Unit
  @Composable get() = run <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
    Unit
  }</fold>
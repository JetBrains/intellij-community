package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

suspend fun awaitApplication(content: @Composable () -> Unit) {}

suspend fun multipleComposable(
  header: @Composable () -> Unit,
  footer: @Composable () -> Unit
) {}

suspend fun mixedNamed(
  composable: @Composable () -> Unit,
  suspendBlock: suspend () -> Unit
) {}

@Composable
suspend fun composableFunction() <fold text='{...}'>{
  awaitApplication(content = <fold text='{...}'>{
    val folded = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  }</fold>)

  multipleComposable<fold text='(...)'>(
    header = <fold text='{...}'>{
      val folded1 = <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>,
    footer = <fold text='{...}'>{
      val folded2 = <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>
  )</fold>

  mixedNamed<fold text='(...)'>(
    composable = <fold text='{...}'>{
      val folded = <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>,
    suspendBlock = <fold text='{...}'>{
      val notFolded = Modifier
        .adjust()
        .adjust()
    }</fold>
  )</fold>
}</fold>
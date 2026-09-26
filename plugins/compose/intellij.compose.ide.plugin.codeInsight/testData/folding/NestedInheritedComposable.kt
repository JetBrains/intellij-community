package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun Container(content: @Composable () -> Unit) {}

@Composable
fun MyScreen() <fold text='{...}'>{
  Container <fold text='{...}'>{
    <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>

    val nonComposableLambda = <fold text='{...}'>{
      val m = Modifier
        .adjust()
        .adjust()
        .adjust()
    }</fold>
  }</fold>
}</fold>

@Composable
fun Row(content: @Composable () -> Unit) {}

@Composable
fun NestedContainerCalls() <fold text='{...}'>{
  Container <fold text='{...}'>{
    Row <fold text='{...}'>{
      Container <fold text='{...}'>{
        <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
        }</fold>
      }</fold>
  }</fold>
}</fold>

@Composable
fun Button(modifier: Modifier = Modifier, content: @Composable () -> Unit) {}

@Composable
fun ChainInArgument() <fold text='{...}'>{
  Button<fold text='(...)'>(
    modifier = <fold text='Modifier.(...)'>Modifier
      .adjust()
      .adjust()</fold>
  )</fold> {}

  Button(modifier = <fold text='Modifier.(...)'>Modifier.adjust().adjust().adjust()</fold>) {}
}</fold>
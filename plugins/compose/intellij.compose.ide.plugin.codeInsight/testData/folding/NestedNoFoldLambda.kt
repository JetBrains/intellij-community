package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun SideEffect(effect: () -> Unit) {}

@Composable
fun LaunchedEffect(key: Any?, block: suspend () -> Unit) {}

@Composable
fun ComposableFunction() <fold text='{...}'>{
  val annotatedLambda = <fold text='{...}'>{
    val notFolded = Modifier
       .adjust()
       .adjust()
   }</fold>

  val explicitLambda: () -> Unit = <fold text='{...}'>{
    val notFolded = Modifier
      .adjust()
      .adjust()
  }</fold>

  SideEffect <fold text='{...}'>{
    val notFolded = Modifier
      .adjust()
      .adjust()
  }</fold>

  LaunchedEffect(Unit) <fold text='{...}'>{
    val notFolded = Modifier
      .adjust()
      .adjust()
  }</fold>
}</fold>
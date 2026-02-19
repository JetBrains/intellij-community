package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

fun ifElseReturn(condition: Boolean): @Composable () -> Unit <fold text='{...}'>{
  return if (condition) <fold text='{...}'>{
    <fold text='{...}'>{
      <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>
  }</fold> else <fold text='{...}'>{
    <fold text='{...}'>{
      <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>
  }</fold>
}</fold>

fun whenReturn(x: Int): @Composable () -> Unit <fold text='{...}'>{
  return when (x) <fold text='{...}'>{
    1 -> <fold text='{...}'>{
      <fold text='{...}'>{
        <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
       }</fold>
     }</fold>
    2 -> <fold text='{...}'>{
      <fold text='{...}'>{
        <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
      }</fold>
    }</fold>
    else -> <fold text='{...}'>{
      <fold text='{...}'>{
        <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
        }</fold>
      }</fold>
    }</fold>
}</fold>

fun nestedIfReturn(a: Boolean, b: Boolean): @Composable () -> Unit <fold text='{...}'>{
  return if (a) <fold text='{...}'>{
    if (b) <fold text='{...}'>{
      <fold text='{...}'>{
        <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
      }</fold>
    }</fold> else <fold text='{...}'>{
      <fold text='{...}'>{
        <fold text='Modifier.(...)'>Modifier
          .adjust()
          .adjust()</fold>
      }</fold>
    }</fold>
  }</fold> else <fold text='{...}'>{
    <fold text='{...}'>{
      <fold text='Modifier.(...)'>Modifier
        .adjust()
        .adjust()</fold>
    }</fold>
  }</fold>
}</fold>
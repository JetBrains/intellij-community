package com.example

import <fold text='...'>androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier</fold>

@Composable
fun WrongTypeChain() <fold text='{...}'>{
  Modifier
    .adjust()
    .adjust()
    .toString()
}</fold>
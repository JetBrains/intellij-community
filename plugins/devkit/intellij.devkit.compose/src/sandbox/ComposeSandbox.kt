// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun ComposeSandbox() {
  // IMPLEMENT YOUR DEMO HERE

  val btnText = remember { mutableStateOf("Awesome, I can do that!") }
  Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Here is your Compose UI playground:", fontStyle = FontStyle.Italic)

    Text("- Implement anything you want here")
    Text("- Click the button below to see how it looks like")
    Text("- Enjoy the power of Compose Desktop")
    Text("- Do not push your changes to Git, they are only for the sandbox")

    OutlinedButton(onClick = {
      btnText.value = "It Works!"
    }) { Text(btnText.value) }
  }
}
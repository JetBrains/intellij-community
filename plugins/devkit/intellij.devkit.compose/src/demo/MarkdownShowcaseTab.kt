// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

@Composable
internal fun MarkdownShowcaseTab(project: Project) {
    VerticallyScrollableContainer {
        var enabled by remember { mutableStateOf(true) }
        var selectable by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CheckboxRow("Enabled", enabled, { enabled = it })
            CheckboxRow("Selectable", selectable, { selectable = it })
        }

        val contentColor = if (enabled) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.disabled
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideMarkdownStyling(project) {
                Markdown(
                    """
                |### Code Blocks With Different Programming Languages
                |```kt
                |class CoolClassKt {
                |    fun main() {
                |        println("This is some Kotlin code!")
                |    }
                |}
                |```
                |
                |```kts
                |class CoolClassKts {
                |    fun main() {
                |        println("This is some Kotlin code!")
                |    }
                |}
                |```
                |
                |```kotlin
                |class CoolClassKotlin {
                |    fun main() {
                |        println("This will only be highlighted if there's an IntelliJ Plugin installed that knows Kotlin")
                |    }
                |}
                |```
                |
                |```py
                |def main():
                |    print("This is some Python code with 'py' as the code block info")
                |
                |if __name__ == "__main__":
                |    main()
                |```
                |
                |```python
                |def main():
                |    print("This is some Python code with 'python' as the code block info")
                |
                |if __name__ == "__main__":
                |    main()
                |```
                |
                |```js
                |function main() {
                |    console.log("This is some JavaScript code!")
                |}
                |```
                |
                |```bat
                |@echo off
                |echo Hello, World! This is a bat code!
                |pause
                |```
                |
                |```clj
                |(print "Hello, World! This is some Clojure code")
                |```
                |
                |```dockerfile
                |# Dockerfile example
                |WORKDIR /app
                |COPY . /app
                |RUN pip install --no-cache-dir -r requirements.txt
                |```
                |
                |```html
                |<h1>This is heading 1</h1>
                |```
                |
                |```regex
                |/```(. *?)```/gs
                |```
                |
                |```
                |Plain code block
                |```
                |
                |    Indented code here!
                """
                        .trimMargin(),
                    Modifier.fillMaxWidth()
                        .background(JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toComposeColor(), RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toComposeColor(),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(8.dp),
                    enabled = enabled,
                    selectable = selectable,
                    onUrlClick = { url -> BrowserUtil.open(url) },
                )
            }
        }
    }
}

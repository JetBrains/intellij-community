// "Fix all 'Composable function call without Compose plugin' problems in file" "false"
// TOOL: com.intellij.compose.ide.plugin.codeInsight.inspections.ComposeMissingPluginInspection
import androidx.compose.runtime.Composable

@Composable
fun Greeting() {
}

@Composable
fun App() {
    Greeting()
    Greeting()
}

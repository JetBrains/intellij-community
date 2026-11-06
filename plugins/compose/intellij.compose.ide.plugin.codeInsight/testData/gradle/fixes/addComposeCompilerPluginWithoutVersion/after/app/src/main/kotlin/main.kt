// "Add Compose compiler plugin to build.gradle.kts" "true"
// TOOL: com.intellij.compose.ide.plugin.codeInsight.inspections.ComposeMissingPluginInspection
import androidx.compose.runtime.Composable

@Composable
fun Greeting() {
}

@Composable
fun App() {
    Greeting()
}
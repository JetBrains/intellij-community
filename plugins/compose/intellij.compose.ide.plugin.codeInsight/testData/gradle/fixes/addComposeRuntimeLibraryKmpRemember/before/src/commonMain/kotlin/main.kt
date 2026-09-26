// "Add Compose runtime dependency" "true"
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

fun App() {
    val state = remember<caret> { mutableStateOf(0) }
}
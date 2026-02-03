// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:-ReferencesToSyntheticJavaProperties
import java.lang.Thread

fun f(thread: Thread) {
    val v = thread::<caret>
}

// EXIST_JAVA_ONLY: { itemText: "getName", tailText: "()", attributes: "bold" }
// EXIST_JAVA_ONLY: { itemText: "setName", attributes: "bold" }
// ABSENT: name
// EXIST_JAVA_ONLY: { itemText: "isDaemon", tailText: "()", attributes: "bold" }
// EXIST_JAVA_ONLY: { itemText: "setDaemon", attributes: "bold" }
// ABSENT: { itemText: "isDaemon", tailText: " (from isDaemon()/setDaemon())" }

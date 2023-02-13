// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
import java.lang.Thread

fun f(thread: Thread) {
    val v = thread::<caret>
}

// EXIST_JAVA_ONLY: { itemText: "name", tailText: " (from getName()/setName())", attributes: "bold" }
// ABSENT: getName
// ABSENT: setName
// EXIST_JAVA_ONLY: { itemText: "isDaemon", tailText: " (from isDaemon()/setDaemon())" }
// ABSENT: { itemText: "isDaemon", tailText: "()", attributes: "bold" }
// ABSENT: setDaemon

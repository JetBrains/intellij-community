// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
import java.lang.Thread

val v = Thread::<caret>

// INVOCATION_COUNT: 2
// EXIST_JAVA_ONLY: { itemText: "isDaemon", tailText: " (from isDaemon()/setDaemon())" }
// EXIST_JAVA_ONLY: { itemText: "isDaemon", tailText: "()", attributes: "bold" }
// EXIST_JAVA_ONLY: setDaemon

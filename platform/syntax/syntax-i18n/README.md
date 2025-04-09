# Syntax i18n Library

Kotlin-multiplatform compatible ResourceBundle implementation for using in syntax implementations.
Only JVM implementation actually depends on `intellij.platform.util` module.
WasmJs part does not have dependencies.
 
Example of usage:

```kotlin
object JavaSyntaxBundle {
  const val BUNDLE: @NonNls String = "messages.JavaSyntaxBundle"

  val resourceBundle: ResourceBundle = ResourceBundle("com.intellij.java.syntax.JavaSyntaxBundle", BUNDLE, this)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return resourceBundle.message(key, *params)
  }

  @JvmStatic
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): () -> @Nls String {
    return resourceBundle.messagePointer(key, *params)
  }
}
```
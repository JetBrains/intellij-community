import com.intellij.openapi.extensions.ExtensionPointName

class MyStringEPConstructor {

  companion object {
    val EP_<caret>NAME = ExtensionPointName<String>("com.intellij.myStringEP")
  }
}
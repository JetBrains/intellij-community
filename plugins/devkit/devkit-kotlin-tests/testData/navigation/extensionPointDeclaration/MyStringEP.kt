import com.intellij.openapi.extensions.ExtensionPointName

class MyStringEP {

  companion object {
    private val EP_<caret>NAME = ExtensionPointName.create<String>("com.intellij.myStringEP")
  }
}
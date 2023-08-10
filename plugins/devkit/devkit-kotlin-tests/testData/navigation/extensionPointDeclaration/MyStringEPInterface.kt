import com.intellij.openapi.extensions.ExtensionPointName
interface MyStringEPInterface {

  companion object {
    private val EP_<caret>NAME = ExtensionPointName.create<String>("com.intellij.myStringEP")
  }
}
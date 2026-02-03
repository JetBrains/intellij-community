import com.intellij.openapi.extensions.ExtensionPointName

class MyStringEPViaInterpolation {

  companion object {
    const val EP_SHORT_NAME = "myStringEP"
    private val EP_<caret>NAME = ExtensionPointName.create<String>("com.intellij.$EP_SHORT_NAME")
  }
}
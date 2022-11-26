import com.intellij.openapi.extensions.ProjectExtensionPointName

class MyStringProjectEP {

  companion object {
    private val EP_<caret>NAME = ProjectExtensionPointName<String>("com.intellij.myStringEP")
  }
}
import com.intellij.lang.LanguageExtension

class MyStringEPLanguageExtension {

  companion object {
    val EP_<caret>NAME = LanguageExtension<String>("com.intellij.myStringEP", "My Default Implementation")
  }
}
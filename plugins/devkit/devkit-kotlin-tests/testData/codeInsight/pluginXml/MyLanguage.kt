@file:Suppress("unused")

class MyLanguage : com.intellij.lang.Language {
  companion object {
    const val PREFIX = "My"
    const val ID = "LanguageID"
    const val ANONYMOUS_ID = "AnonymousLanguageID"

    val ANONYMOUS_LANGUAGE: com.intellij.lang.Language = object : MySubLanguage(PREFIX + ANONYMOUS_ID, "MyDisplayName") {}
  }

  @Suppress("ConvertSecondaryConstructorToPrimary")
  constructor() : super(PREFIX + ID)

  private open class MySubLanguage(id: String?, private val myName: String) : com.intellij.lang.Language(id!!) {
    override fun getDisplayName(): String = myName
  }

  abstract class AbstractLanguage protected constructor() : com.intellij.lang.Language("AbstractLanguageIDMustNotBeVisible")
}
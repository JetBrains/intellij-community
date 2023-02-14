import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

object SingletonFileType : LanguageFileType(Language.ANY) {
  @NonNls
  override fun getDefaultExtension(): String {
    return "any"
  }

  override fun getDescription(): String {
    return "any"
  }

  override fun getIcon(): Icon? {
    return null
  }

  @NonNls
  override fun getName(): String {
    return "any"
  }
}

class MyFileType : LanguageFileType(Language.ANY) {
  @NonNls
  override fun getDefaultExtension(): String {
    return "any"
  }

  override fun getDescription(): String {
    return "any"
  }

  override fun getIcon(): Icon? {
    return null
  }

  @NonNls
  override fun getName(): String {
    return "any"
  }
}

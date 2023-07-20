import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

object SingletonFileType : LanguageFileType(Language.ANY) {
  override fun getDefaultExtension() = "any"
  override fun getDescription() = "any"
  override fun getIcon() = null
  override fun getName() = "any"
}

class MyFileType : LanguageFileType(Language.ANY) {
  override fun getDefaultExtension() = "any"
  override fun getDescription() = "any"
  override fun getIcon() = null
  override fun getName() = "any"
}

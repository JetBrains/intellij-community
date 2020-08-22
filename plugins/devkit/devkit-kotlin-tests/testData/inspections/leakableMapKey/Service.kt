import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import java.util.*

class Service {

  private val languageMap: <warning descr="Consider using 'String' instead of 'Language' as the map key">Map<Language, Any></warning> = HashMap()
  private val <warning descr="Consider using 'String' instead of 'Language' as the map key">languageHashMap</warning> = HashMap<Language, Any>()
  private val languageMutableMap: <warning descr="Consider using 'String' instead of 'Language' as the map key">MutableMap<Language, Any></warning> = HashMap()
  private val languageMap2: <warning descr="Consider using 'String' instead of 'Language' as the map key">Map<in Language, Any></warning> = HashMap()

  private val fileTypeMap: <warning descr="Consider using 'String' instead of 'FileType' as the map key">Map<FileType, Any></warning> = HashMap()
  private val fileTypeTreeMap: <warning descr="Consider using 'String' instead of 'FileType' as the map key">TreeMap<FileType, Any></warning> = TreeMap()
  private val fileTypeMap2: <warning descr="Consider using 'String' instead of 'FileType' as the map key">Map<out FileType, Any></warning> = HashMap()
  private val languageFileTypeMap: <warning descr="Consider using 'String' instead of 'LanguageFileType' as the map key">Map<LanguageFileType, Any></warning> = HashMap()

  private val objectMap: Map<Any, Any> = HashMap()
  private val objectTreeMap: TreeMap<Any, Any> = TreeMap()
}
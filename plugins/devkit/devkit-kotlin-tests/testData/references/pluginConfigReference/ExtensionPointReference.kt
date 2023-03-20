package references.pluginConfigReference

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.util.KeyedExtensionCollector

class ExtensionPointReference {

  var EP_NAME_CREATE = ExtensionPointName.create<String>("plugin.id.ep.name")
  var INVALID_EP_NAME_CREATE = ExtensionPointName.create<String>("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>")

  val EPN_CTOR= ExtensionPointName<String>("plugin.id.ep.name")
  val INVALID_EPN_CTOR = ExtensionPointName<String>("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>")

  val LANGUAGE_EXTENSION = LanguageExtension<String>("plugin.id.ep.name")
  val INVALID_LANGUAGE_EXTENSION = LanguageExtension<String>("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>")

  val PROJECT_EPN_CTOR = ProjectExtensionPointName<String>("plugin.id.ep.name")
  val INVALID_PROJECT_EPN_CTOR = ProjectExtensionPointName<String>("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>")

  // -------------

  val INVALID_KEC_CTOR = KeyedExtensionCollector<String,String>("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>")

  class KeyedExtensionCollectorSubClass : KeyedExtensionCollector<Any?, Any?>("plugin.id.ep.name")
  class KeyedExtensionCollectorSubClassInvalid : KeyedExtensionCollector<Any?, Any?>("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>")

}
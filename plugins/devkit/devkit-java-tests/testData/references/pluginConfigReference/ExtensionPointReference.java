import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.extensions.ProjectExtensionPointName;

public class ExtensionPointReference {
  ExtensionPointName<String> EP_NAME = ExtensionPointName.create("plugin.id.ep.name");
  ExtensionPointName<String> EP_QUALIFIED_NAME = ExtensionPointName.create("ep.qualified.name");

  LanguageExtension<String> LANGUAGE_EXTENSION = new LanguageExtension<>("plugin.id.ep.name");
  LanguageExtension<String> INVALID_LANGUAGE_EXTENSION = new LanguageExtension<>("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>");

  ExtensionPointName<String> INVALID_EPN_CREATE = ExtensionPointName.create("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>");
  ExtensionPointName<String> INVALID_EPN_CTOR = new ExtensionPointName("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>");

  ProjectExtensionPointName<String> INVALID_PROJECT_EPN_CTOR = new ProjectExtensionPointName("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>");

  KeyedExtensionCollector<String,String> INVALID_KEC_CTOR = new KeyedExtensionCollector("<error descr="Cannot resolve extension point 'INVALID_VALUE'">INVALID_VALUE</error>");

}
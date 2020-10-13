import com.intellij.openapi.util.registry.Registry;

public class RegistryKeyId {

  public static void main(String[] args) {
    Registry.intValue("vcs.showConsole"); // registry.properties
    Registry.intValue("my.plugin.key"); // registryKey.xml

    Registry.get("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
    Registry.is("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
    Registry.intValue("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
    Registry.doubleValue("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
    Registry.stringValue("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
    Registry.getColor("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>", null);
  }
}
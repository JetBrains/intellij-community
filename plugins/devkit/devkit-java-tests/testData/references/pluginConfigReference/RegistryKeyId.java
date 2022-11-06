import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;

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

  public static void testRegistryManager() {
    RegistryManager registryManager = RegistryManager.getInstance();
    registryManager.is("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
    registryManager.intValue("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
    registryManager.intValue("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>", 123);
    registryManager.get("<error descr="Cannot resolve registry key 'INVALID_VALUE'">INVALID_VALUE</error>");
  }
}
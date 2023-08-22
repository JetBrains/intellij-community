import com.intellij.openapi.extensions.PluginId;

class UsePluginIdEquals {

  void any() {
    PluginId id1 = PluginId.getId("id1");
    PluginId id2 = PluginId.getId("id2");

    boolean result = !id1.equals(id2);
  }

}

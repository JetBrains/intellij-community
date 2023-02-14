import com.intellij.openapi.extensions.PluginId;

class UsePluginIdEquals {

  void any() {
    PluginId id1 = PluginId.getId("id1");
    PluginId id2 = PluginId.getId("id2");

    boolean result = <warning descr="'PluginId' instances should be compared for equality, not identity">id1<caret> == id2</warning>;
  }

}

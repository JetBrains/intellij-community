import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.KeyedLazyInstance;

public class MyStringEP {

    public static final ExtensionPointName<KeyedLazyInstance<String>> EP_<caret>NAME =
      ExtensionPointName.create("com.intellij.myStringEP");

}
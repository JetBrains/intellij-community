import com.intellij.openapi.extensions.ExtensionPointName;

import java.lang.String;

public class MyStringEP {

    public static final ExtensionPointName<String> EP_<caret>NAME =
      ExtensionPointName.create("com.intellij.myStringEP");

}
import com.intellij.openapi.extensions.ExtensionPointName;

import java.lang.String;

public class MyStringEPConstructor {

    // private is allowed
    private static final ExtensionPointName<String> EP_<caret>NAME =
      new ExtensionPointName<String>("com.intellij.myStringEP");

}
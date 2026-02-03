import com.intellij.openapi.extensions.ExtensionPointName;

import java.lang.String;

public class MyStringEPViaConstant {

    public static final String EP_ID = "com.intellij.myStringEP";

    public static final ExtensionPointName<String> EP_<caret>NAME =
      ExtensionPointName.create(EP_ID);

}
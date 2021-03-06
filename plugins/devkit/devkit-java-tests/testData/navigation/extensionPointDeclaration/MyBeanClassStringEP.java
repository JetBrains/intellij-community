import com.intellij.openapi.extensions.ExtensionPointName;

import java.lang.String;

public class MyBeanClassStringEP {

    public static final ExtensionPointName<String> EP_<caret>NAME =
      ExtensionPointName.create("com.intellij.myBeanClassStringEP");

}
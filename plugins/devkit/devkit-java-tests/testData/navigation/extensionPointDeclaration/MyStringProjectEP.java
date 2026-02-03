import com.intellij.openapi.extensions.ProjectExtensionPointName;

import java.lang.String;

public class MyStringProjectEP {

    public static final ProjectExtensionPointName<String> EP_<caret>NAME =
      new ProjectExtensionPointName<>("com.intellij.myStringEP");

}
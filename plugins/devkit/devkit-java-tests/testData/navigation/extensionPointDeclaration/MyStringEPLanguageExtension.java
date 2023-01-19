import com.intellij.lang.LanguageExtension;
import java.lang.String;

public class MyStringEP {

    public static final LanguageExtension<String> EP_<caret>NAME =
      new LanguageExtension("com.intellij.myStringEP", "My Default Implementation");

}
import com.intellij.lang.LanguageExtension;

public class MyStringEPViaLanguageExtensionSuperCall extends LanguageExtension<String> {
  private My<caret>StringEPViaLanguageExtensionSuperCall() {
    super("com.intellij.myStringEP")
  }
}
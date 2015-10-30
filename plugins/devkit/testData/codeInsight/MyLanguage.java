public class MyLanguage extends com.intellij.lang.Language {
  public static final MyLanguage ANONYMOUS_LANUAGE = new MyLanguage("MyAnonymousLanguageID") {}; 

  public MyLanguage() {
    super("MyLanguageID");
  }
  
  public MyLanguage(String ID) {
    super(ID);
  }
}
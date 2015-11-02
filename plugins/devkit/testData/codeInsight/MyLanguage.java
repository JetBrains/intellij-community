public class MyLanguage extends com.intellij.lang.Language {
  public static final MyLanguage ANONYMOUS_LANUAGE = new MyLanguage("MyAnonymousLanguageID") {}; 
  public static final MyLanguage ANONYMOUS_LANUAGE_WITH_NAME_FROM_PROPERTIES = new MyLanguage(MyBundle.message("language.name")) {}; 

  public MyLanguage() {
    super("MyLanguageID");
  }
  
  public MyLanguage(String ID) {
    super(ID);
  }
}
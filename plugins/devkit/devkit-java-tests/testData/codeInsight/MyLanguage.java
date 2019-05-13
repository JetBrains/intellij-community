public class MyLanguage extends com.intellij.lang.Language {
  
  public static final com.intellij.lang.Language ANONYMOUS_LANGUAGE =
    new MySubLanguage("MyAnonymousLanguageID", "MyDisplayName") {};

  public MyLanguage() {
    super("MyLanguageID");
  }

  private static class MySubLanguage extends com.intellij.lang.Language {
    private final String myName;

    public MySubLanguage(final String id, @NotNull String name) {
      super(id);
      myName = name;
    }

    public String getDisplayName() {
      return myName;
    }
  }


  public static class AbstractLanguage extends Language {
    protected AbstractLanguage() {
      super("AbstractLanguageIDMustNotBeVisible");
    }
  }

}
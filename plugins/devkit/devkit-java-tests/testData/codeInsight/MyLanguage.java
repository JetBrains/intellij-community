public class MyLanguage extends com.intellij.lang.Language {
  public static final String PREFIX = "My"
  public static final String ID = "LanguageID"
  public static final String ANONYMOUS_ID = "AnonymousLanguageID"

  public static final com.intellij.lang.Language ANONYMOUS_LANGUAGE =
    new MySubLanguage(PREFIX + ANONYMOUS_ID, "MyDisplayName") {};

  public MyLanguage() {
    super(PREFIX + ID);
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


  public abstract static class AbstractLanguage extends com.intellij.lang.Language {
    protected AbstractLanguage() {
      super("AbstractLanguageIDMustNotBeVisible");
    }
  }

}
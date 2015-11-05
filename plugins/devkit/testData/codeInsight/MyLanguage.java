public class MyLanguage extends com.intellij.lang.Language {
  public static final com.intellij.lang.Language ANONYMOUS_LANUAGE = new MySubLanguage("MyAnonymousLanguageID", "MyDisplayName") {
  };
  public static final MyLanguage ANONYMOUS_LANUAGE_WITH_NAME_FROM_PROPERTIES = new MyLanguage(MyBundle.message("language.name")) {
  };

  public MyLanguage() {
    super("MyLanguageID");
  }

  public MyLanguage(String ID) {
    super(ID);
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
}
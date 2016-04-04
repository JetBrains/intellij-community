import org.jetbrains.annotations.PropertyKey;

public class MyBundle {
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return ourInstance.getMessage(key, params);
  }

  @NonNls
  private static final String BUNDLE = "MyBundle";
  private static final MyBundle ourInstance = new MyBundle();

  private MyBundle() {
    super(BUNDLE);
  }
}

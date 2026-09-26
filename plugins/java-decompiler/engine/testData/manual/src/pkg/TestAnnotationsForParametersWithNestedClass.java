package pkg;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestAnnotationsForParametersWithNestedClass {

  static void run(@Nullable RunCallback callback) {
    if (callback==null) {
      return;
    }
    callback.run();
  }

  static void run2(@NotNull RunCallback callback) {
    callback.run();
  }

  static void run3(@NotNull String msg, @NotNull RunCallback callback) {
    callback.run();
  }


  public interface RunCallback {
    void run();
  }


  static void main() {
    System.out.println("21211");
  }
}
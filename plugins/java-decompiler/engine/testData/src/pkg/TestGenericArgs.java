package pkg;

public class TestGenericArgs {
  private static <T> void genericSingle(Class<T> param) { }

  private static <T> void genericVarArgs(Class<T>... param) { }

  private static <T> void genericArray(Class<T>[] param) { }

  private static <T> void single(Class param) { }

  private static <T> void varArgs(Class... param) { }

  private static <T> void array(Class[] param) { }
}
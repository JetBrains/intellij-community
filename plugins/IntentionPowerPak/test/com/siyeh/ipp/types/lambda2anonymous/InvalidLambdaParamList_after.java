import java.util.function.BiFunction;

class Test
{
  BiFunction f = new BiFunction() {
      @Override
      public Object apply(Object a, Object o2) {
          return 1;
      }
  };
}

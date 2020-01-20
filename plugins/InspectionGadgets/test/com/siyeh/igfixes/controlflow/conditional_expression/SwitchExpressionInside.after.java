import java.io.Serializable;

class ConvertToIf {

  void test(boolean b, int i) {
    Class<?> c;
      if (b) c = (switch (i) {
          case 1 -> true;
          default -> 0;
      }).getClass();
      else c = ((Serializable) 1).getClass();

      System.out.println(c);
  }
}

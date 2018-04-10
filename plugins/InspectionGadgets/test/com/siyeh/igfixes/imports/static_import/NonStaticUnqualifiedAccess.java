import static//end of line comment
  java.lang.Lon<caret>g.*;
public class Foo implements Runnable {
    @Override
    public void run() {
        System.out.println(getLong("123"));
        System.out.println(getClass());
    }
}
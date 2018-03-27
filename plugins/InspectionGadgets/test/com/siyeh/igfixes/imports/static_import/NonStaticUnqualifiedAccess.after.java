//end of line comment

public class Foo implements Runnable {
    @Override
    public void run() {
        System.out.println(Long.getLong("123"));
        System.out.println(getClass());
    }
}
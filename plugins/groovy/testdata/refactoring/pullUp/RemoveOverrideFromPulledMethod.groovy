public class Test {
    abstract class Base extends Int {
        @Override
        public abstract String<caret> foo();
    }

    class Int {
    }
}

public interface Anonymous<T> {}

class Test {
        public static void main(String[] args) {
                Anonymous<Integer> anon = new Anonymous<<caret>>() {};
        }
}
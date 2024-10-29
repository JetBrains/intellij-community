public class RecordHighlighting {

    public static void main(String[] args) {

    }

    sealed interface A {

    }

    record B() implements A {
    }

    record C() implements A {
    }
}
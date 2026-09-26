public interface Bar extends Foo {

    @Override
    default String foo() {
        return "";
    }
}
// "Replace by @DataPoints method" "true"
class Foo {

    @org.junit.experimental.theories.DataPoints
    public static int[] myData() {
        return new int[]{1, 2, 3};
    }

}
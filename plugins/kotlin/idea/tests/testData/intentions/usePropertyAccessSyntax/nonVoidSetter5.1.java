public class Foo {
    private int first;
    private String second;

    public int getFirst() {
        return first;
    }

    public String getSecond() {
        return second;
    }

    public Foo setFirst(int first) {
        this.first = first;
        return this;
    }

    public Foo setSecond(String second) {
        this.second = second;
        return this;
    }
}
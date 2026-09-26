public class CounterHolder {
    public int calls = 0;

    public String getValue() {
        calls++;
        return calls == 1 ? "x" : "b";
    }
}

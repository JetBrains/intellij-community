package pkg;

public class TestMultipleDeclarations {

    public int seeXenoAmess() {
        int result = 1;
        Object e = this.getXenoAmess();
        result = result * 5 + (e == null ? 7 : e.hashCode());
        return result;
    }

    private Object getXenoAmess() {
        return "XenoAmess";
    }
}

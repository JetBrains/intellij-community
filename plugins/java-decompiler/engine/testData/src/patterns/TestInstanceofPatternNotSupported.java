package decompiler;

public class TestInstanceofPatternNotSupported {
    void typePattern(Object str) {
        if (!(str instanceof String)) {
            System.out.println("no");
            return;
        }
        String s = (String) str;
        if (s.length() > 3) {
            System.out.println(s);
        } else if (s.startsWith("a")) {
            System.out.println(s + "");
        }
    }
}

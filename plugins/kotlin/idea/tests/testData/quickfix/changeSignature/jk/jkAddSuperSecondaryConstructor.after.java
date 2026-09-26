// "Add secondary constructor to 'KotlinBase'" "true"

public class JavaInheritor extends KotlinBase {
    public JavaInheritor(String s) {
        super(s);
    }

    public JavaInheritor(String s, int i) {
        super(s, i<selection><caret></selection>);
    }
}

// "Add secondary constructor to 'KotlinBase'" "false"
// ACTION: Create method 'foo' in 'JavaInheritor'
// ACTION: Rename reference

public class JavaInheritor extends KotlinBase {
    public JavaInheritor(String s) {
        super(s);
    }

    void usage() {
        <caret>foo(true);
    }
}
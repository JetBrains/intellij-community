// "Add 'String' as 1st parameter to constructor 'KotlinBase'" "false"
// ACTION: Create method 'foo' in 'JavaInheritor'
// ACTION: Rename reference

public class JavaInheritor extends KotlinBase {
    public JavaInheritor(String s) {
        super();
    }

    void usage() {
        <caret>foo("");
    }
}
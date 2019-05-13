
import java.util.ArrayList;
import java.util.LinkedList;


class InspecitonIssue {
        void foo(ArrayList<String> lst) {}

        void bar() {
                foo(new LinkedList<<caret>>());
        }
}
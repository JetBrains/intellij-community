package one.two;

import main.another.one.two.ObjectKotlin;

public class MainJava {
    public String field = "";

    public static class NestedJava extends MainJava {

    }

    public class InnerJava extends MainJava {

    }

    public class InnerJava2 extends InnerJava {

    }

    public static class Wrapper {
        public static class NestedWrapper extends ObjectKotlin.NestedObjectKotlin.NestedNestedKotlin {

        }
    }
}


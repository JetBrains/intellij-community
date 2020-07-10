public class Main {

    private static final Integer field = 322;

    public static void main(String[] args) {
        StaticClass staticClass = new StaticClass();
        int abc = 3;
        <caret>int bcd = 5 + field;
        function(true);
        if(abc < bcd && function(true)) {
            System.out.println(abc * bcd - field);
        }
        staticClass.main(null && field > 0);
    }

    private static boolean function(boolean arg) {
        return arg;
    }

    private static class StaticClass {
        public static void main(String[] args) {
            function(false);
        }

        private static boolean function(boolean arg) {
            return arg && function(function && function || arg);
        }

        private static boolean function = true;
    }
}

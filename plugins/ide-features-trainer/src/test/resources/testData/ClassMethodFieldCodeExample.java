public class Main {

    private static final Integer field = 322;

    public static void main(String[] args) {
        StaticClass staticClass = new StaticClass();
        int abc = 3;
        <caret>int bcd = 5;
        function(true);
        if(abc < bcd && function(true)) {
            System.out.println(abc * bcd);
        }
        staticClass.main(null);
    }

    private static boolean function(boolean arg) {
        return arg;
    }

    private static class StaticClass {
        public static void main(String[] args) {
            function(false);
        }
    }
}

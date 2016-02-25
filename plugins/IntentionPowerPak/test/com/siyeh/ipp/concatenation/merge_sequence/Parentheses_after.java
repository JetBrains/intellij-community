
class Parentheses {

    public static void main(String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append("a");
        (sb).append("B").append('c');
        sb.toString();
    }
}
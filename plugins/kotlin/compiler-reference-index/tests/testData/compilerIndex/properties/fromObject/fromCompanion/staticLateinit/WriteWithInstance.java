public class WriteWithInstance {
    public static void main(String[] args) {
        Main.Companion.setStaticLateinit(new Main());
    }
}
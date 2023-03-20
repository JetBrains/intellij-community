public class WriteWithInstance {
    public static void main(String[] args) {
        Main.Companion.setCompanionExtensionProperty(42, 3);
    }
}
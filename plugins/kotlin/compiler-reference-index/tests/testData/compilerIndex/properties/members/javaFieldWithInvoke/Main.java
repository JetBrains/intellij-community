public class Main {
    public void invoke() {
    }

    public static Other INST<caret>ANCE = new Other();

    public static class Other extends Main {
    }
}
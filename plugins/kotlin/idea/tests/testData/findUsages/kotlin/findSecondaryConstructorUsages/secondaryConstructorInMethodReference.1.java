import java.util.stream.Stream;
public class Main {
    public static void main(String[] args) {
        Stream.of("b").map(B::new);
    }
}
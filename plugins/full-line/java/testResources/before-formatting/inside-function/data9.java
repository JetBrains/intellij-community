import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        Stream.of(1, 2)
                .map(x -> x)
                .forEach(<caret>)
    }
}
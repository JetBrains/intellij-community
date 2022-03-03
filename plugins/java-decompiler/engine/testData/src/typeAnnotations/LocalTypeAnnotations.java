package typeAnnotations;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;

public class LocalTypeAnnotations {
//    void simple() {
//        @A String hello = "Hello";
//        @B String space = " ";
//        @C String world = "World";
//    }
//
//    void instanceOf() {
//        boolean isobj = "Hello world!" instanceof @B Object;
//    }
//
//    void newArray() {
//        @A String @B [] hw = new @C String @D [0];
//    }
//
//    void methodRef() {
//        BiFunction<String, Integer, Character> stringIntegerCharacterBiFunction = @C String::charAt;
//    }
//
//    void typeArg() {
//        BiFunction<@D String, @E Integer, @F Character> stringIntegerCharacterBiFunction = String::charAt;
//    }
//
//    void tryWithResources() {
//        try (@A BufferedReader br = new @B BufferedReader(new @C FileReader("test"))) {
//        } catch (@A FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (@B IOException e) {
//            e.printStackTrace();
//        }
//    }

    void combined() {
        @A String hello = "Hello!";
        boolean isobj = hello instanceof @B Object;
        @C String world = " World!";
        BiFunction<String, Integer, Character> stringIntegerCharacterBiFunction = @D String::charAt;
        try (BufferedReader br = new BufferedReader(new FileReader("test"))) {
        } catch (@E IOException e) {
            e.printStackTrace();
        }
        @F String test = " Test!";
    }
}

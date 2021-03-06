import java.util.function.Consumer;
import java.lang.annotation.*;

class X {
    private static void accept(@AAA Integer @AAA [] i) {
        System.out.println(i);
    }

    @Target({ElementType.TYPE_USE, ElementType.PARAMETER})
    @interface AAA {}
  
    Consumer<Integer[]> c1 = X::<caret>accept;
}
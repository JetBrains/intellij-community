package typeAnnotations;

import java.util.List;
import java.util.Map;

public class GenericTypeAnnotations {
    @A Map<? extends String, List<Object>> m1;
    Map<@B ? extends String, List<Object>> m2;
    Map<? extends @C String, List<Object>> m3;
    Map<? extends String, @D List<Object>> m4;
    Map<? extends String, List<@E Object>> m5;
    @A @B Map<? extends String, List<Object>> m6;
    Map<@A @B ? extends String, List<Object>> m7;
    Map<? extends @C @D String, List<Object>> m8;
    Map<? extends String, @D @E List<Object>> m9;
    Map<? extends String, List<@E @F Object>> m10;
    @A Map<@B ? extends @C String, @L @D List<@E Object>> m11;
    @A Map<@A Object, @B List<@C Object>> m12() { return null; }
    @A @B List<@C Object> l1() { return null; }
    @L Map<? extends String, List<Object>> m13;
}

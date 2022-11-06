import java.io.Serializable;

public abstract class LambdaRefInnerClass<T extends Serializable> {
    public abstract class Holder { }

    public static final LongKey<LambdaRefInnerClass.Holder> func = va<caret>lue -> 0L;
}

interface LongKey<V> extends Serializable {
    Long getKey(V value);
}
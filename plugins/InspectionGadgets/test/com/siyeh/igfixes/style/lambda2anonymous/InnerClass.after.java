import java.io.Serializable;

public abstract class LambdaRefInnerClass {
    public abstract class Holder { }

    public static final LongKey<Holder> func = new LongKey<Holder>() {
        @Override
        public Long getKey(Holder value) {
            return 0L;
        }
    };
}

interface LongKey<V> extends Serializable {
    Long getKey(V value);
}
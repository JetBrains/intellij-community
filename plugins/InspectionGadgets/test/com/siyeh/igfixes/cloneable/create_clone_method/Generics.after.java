class Generics<A, B, C> implements Cloneable {
    private String[] ss;

    @Override
    public Generics<A, B, C> clone() {
        try {
            Generics clone = (Generics) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
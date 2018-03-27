class Generics<A, B, C> implements Cloneable {


    @Override
    public Generics<A, B, C> clone() throws CloneNotSupportedException {
        return (Generics) super.clone();
    }
}
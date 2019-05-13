class Generic<T> extends Parent {

    @Override
    protected Generic<T> clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
}

class Parent implements Cloneable {
}
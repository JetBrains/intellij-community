class Simple extends Parent {

    protected Simple clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
}
class Parent implements Cloneable {


}
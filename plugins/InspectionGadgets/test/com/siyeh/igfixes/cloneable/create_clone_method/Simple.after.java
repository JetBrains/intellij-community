class Simple implements Cloneable {


    @Override
    public Simple clone() throws CloneNotSupportedException {
        return (Simple) super.clone();
    }
}
package source;

abstract class X<S> implements T<S> {

    @Override
    public S getFoo() {
        return null;
    }
}

class Y implements T<String> {

    @Override
    public String getFoo() {
        return "";
    }
}

class Z implements T<Boolean> {
    @Override
    public Boolean getFoo() {
        return null;
    }
}

interface U extends T<Object> {

}
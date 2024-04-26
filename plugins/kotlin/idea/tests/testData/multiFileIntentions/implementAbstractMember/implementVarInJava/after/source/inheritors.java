package source;

abstract class X<S> implements T<S> {
    @Override
    public S getFoo() {
        return null;
    }

    @Override
    public void setFoo(S s) {

    }
}

class Y implements T<String> {
    @Override
    public void setFoo(String s) {

    }

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

    @Override
    public void setFoo(Boolean b) {

    }
}

class W implements T<Integer> {

    @Override
    public Integer getFoo() {
        return 0;
    }

    @Override
    public void setFoo(Integer integer) {

    }
}

interface U extends T<Object> {

}
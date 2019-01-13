import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    String[] bar1() {
        List<Double> elemen<caret>ts = [2]
        bar2([2])
        return [""]
    }

    void bar2(List<Object> objects) {
        for (Integer num: objects){
        }
    }
}
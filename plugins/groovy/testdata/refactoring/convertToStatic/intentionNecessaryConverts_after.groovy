import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    String[] bar1() {
        List<Double> elements = [2] as List<Double>
        bar2([2] as List<Object>)
        return [""] as String[]
    }

    void bar2(List<Object> objects) {
        for (Integer num: (objects as List<Integer>)){
        }
    }
}
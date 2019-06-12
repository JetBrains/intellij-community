import groovy.transform.CompileStatic

@CompileStatic
class Bar {
    String foo() {
        List<String> strings = []
        strings << ("${1 + 1}" as String)
        return strings.get(0)
    }
}

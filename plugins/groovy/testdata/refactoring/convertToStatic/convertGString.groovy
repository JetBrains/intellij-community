class Bar {
    String foo() {
        List<String> strings = []
        strings << "${1+1}"
        return strings.get(0)
    }
}

enum class Main {
    <caret>K;

    enum class Test {
        A;

        fun test() {
            Test.values()
        }

    }
}

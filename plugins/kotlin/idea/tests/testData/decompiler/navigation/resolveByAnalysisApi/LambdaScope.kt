import testData.libraries.*

fun AScope.main() {
    withLambda {
        while (true) {
            f<caret>oo("")
        }
    }
}
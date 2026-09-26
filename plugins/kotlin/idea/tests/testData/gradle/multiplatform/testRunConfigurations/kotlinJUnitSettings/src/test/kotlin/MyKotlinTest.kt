import org.junit.Test

class <lineMarker descr="Run Test" settings=":test --tests \"MyKotlinTest\"">MyKotlinTest</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" settings=":test --tests \"MyKotlinTest.testA\"">testA</lineMarker>() {
    }
}

object MyKotlinTestMain {
    @JvmStatic
    fun <lineMarker descr="null" mainClass="MyKotlinTestMain">main</lineMarker>(args: Array<String>) {
    }
}

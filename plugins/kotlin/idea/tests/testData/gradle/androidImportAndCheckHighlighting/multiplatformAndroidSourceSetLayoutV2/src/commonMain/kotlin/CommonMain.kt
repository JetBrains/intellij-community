object CommonMain {
    operator fun invoke() = CommonMain
}

expect fun <lineMarker descr="Has actuals in [project.jvmMain, project.main] modules">commonMainExpect</lineMarker>(): CommonMain

package sample

expect interface <!LINE_MARKER("descr='Has actuals in js module'"), LINE_MARKER("descr='Is subclassed by AbstractInput (sample) JSInput (sample) Press ... to navigate'")!>Input<!>

abstract class <!LINE_MARKER("descr='Is subclassed by JSInput (sample) Press ... to navigate'")!>AbstractInput<!> : Input {
    val head: Int = null!!
}

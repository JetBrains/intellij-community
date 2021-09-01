package sample

expect interface <!LINE_MARKER("descr='Has actuals in JS'"), LINE_MARKER("descr='Is subclassed by AbstractInput JSInput  Click or press ... to navigate'")!>Input<!>

abstract class <!LINE_MARKER("descr='Is subclassed by JSInput  Click or press ... to navigate'")!>AbstractInput<!> : Input {
    val head: Int = null!!
}

package base

interface <!LINE_MARKER("descr='Is implemented by CheckClass (base) SubCheck SubCheckClass Press ... to navigate'")!>Check<!> {
    fun <!LINE_MARKER("descr='Is overridden in SubCheck Press ... to navigate'")!>test<!>(): String {
        return "fail";
    }
}

open class <!LINE_MARKER("descr='Is subclassed by SubCheckClass Press ... to navigate'")!>CheckClass<!> : Check

package base

interface <!LINE_MARKER("descr='Is implemented by CheckClass SubCheckClass SubCheck  Click or press ... to navigate'")!>Check<!> {
    fun <!LINE_MARKER("descr='Is overridden in SubCheck'")!>test<!>(): String {
        return "fail";
    }
}

open class <!LINE_MARKER("descr='Is subclassed by SubCheckClass  Click or press ... to navigate'")!>CheckClass<!> : Check

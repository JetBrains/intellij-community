import base.*

interface <!LINE_MARKER("descr='Is implemented by SubCheckClass Press ... to navigate'")!>SubCheck<!> : Check {
    override fun <!LINE_MARKER("descr='Overrides function in Check (base) Press ... to navigate'")!>test<!>(): String {
        return "OK"
    }
}

class <!EXPLICIT_OVERRIDE_REQUIRED_IN_MIXED_MODE!>SubCheckClass<!> : CheckClass(), SubCheck
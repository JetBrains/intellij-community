package platform.lib

open class <!LINE_MARKER("descr='Is subclassed by MyCancellationException (platform.lib) MyIllegalStateException (platform.lib) OtherException [jvm] Press ... to navigate'")!>MyException<!>

open class <!LINE_MARKER("descr='Is subclassed by MyCancellationException (platform.lib) OtherException [jvm] Press ... to navigate'")!>MyIllegalStateException<!> : MyException()

class MyCancellationException : MyIllegalStateException()
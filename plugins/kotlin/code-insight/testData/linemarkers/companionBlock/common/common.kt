package sample

expect class <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is subclassed by LibClass [jvm] (sample) LibClass [js] (sample) Press ... to navigate'")!>LibClass<!>() {
    fun <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is implemented in LibClass (sample) LibClass (sample) Press ... to navigate'")!>member<!>()

    companion {
        fun <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is implemented in LibClass (sample) LibClass (sample) Press ... to navigate'")!>foo<!>()
        val <!LINE_MARKER("descr='Has actuals in [js, jvm] modules'; targets=[(text=js; icon=nodes/Module.svg); (text=jvm; icon=nodes/Module.svg)]"), LINE_MARKER("descr='Is implemented in LibClass (sample) LibClass (sample) Press ... to navigate'")!>bar<!>: Int
    }
}

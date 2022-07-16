class NavigateFromPrivateAbstractClass {
    private abstract class <lineMarker>Base</lineMarker> {}

    private class Impl: Base() {}

    private class Impl2: Base() {}

}

/*
LINEMARKER: descr='Is subclassed by Impl in NavigateFromPrivateAbstractClass Impl2 in NavigateFromPrivateAbstractClass  Click or press ... to navigate'
TARGETS:
 NavigateFromPrivateAbstractClass.kt
    private class <1>Impl: Base() {}

    private class <2>Impl2: Base() {}
*/
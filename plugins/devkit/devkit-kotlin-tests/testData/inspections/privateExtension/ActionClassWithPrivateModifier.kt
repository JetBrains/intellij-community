import com.intellij.openapi.actionSystem.*

<warning descr="Use 'internal' modifier for extension and service classes registered in plugin.xml instead of 'private'">private</warning> class MyActionImpl : AnAction() {

}

class MyPublicActionImpl : AnAction() {

}

private class MyLocallyDeclaredActionImpl : AnAction() { }
import com.intellij.openapi.Disposable

interface MyDisposable : Disposable

class <error descr="Listener implementation must not implement 'Disposable'">MyProjectListener</error> : BaseListenerInterface, MyDisposable
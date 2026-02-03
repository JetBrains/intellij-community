import com.intellij.openapi.Disposable;

interface MyDisposable extends Disposable {}
class <error descr="Listener implementation must not implement 'Disposable'">MyProjectListener</error> implements BaseListenerInterface, MyDisposable { }
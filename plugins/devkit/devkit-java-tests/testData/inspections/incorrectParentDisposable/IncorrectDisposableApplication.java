import com.intellij.openapi.application.Application;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

public class IncorrectDisposableApplication implements Disposable {

  public void incorrectParentDisposable(Application application) {
    Disposer.register(<warning descr="Don't use Application as disposable in plugin code (Choosing a Disposable Parent)">application</warning>, this);
  }

  @Override
  public void dispose() {
  }
}
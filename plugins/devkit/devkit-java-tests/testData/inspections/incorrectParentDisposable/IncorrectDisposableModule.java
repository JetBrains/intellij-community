import com.intellij.openapi.module.Module;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

public class IncorrectDisposableModule implements Disposable {

  public void incorrectParentDisposable(Module module) {
    Disposer.register(<warning descr="Don't use Module as disposable in plugin code (Choosing a Disposable Parent)">module</warning>, this);
  }

  @Override
  public void dispose() {
  }
}
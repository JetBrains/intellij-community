import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

public class IncorrectDisposableProject implements Disposable {

  public void incorrectParentDisposable(Project project) {
    Disposer.register(<warning descr="Don't use Project as disposable in plugin code (Choosing a Disposable Parent)">project</warning>, this);
  }

  @Override
  public void dispose() {
  }
}
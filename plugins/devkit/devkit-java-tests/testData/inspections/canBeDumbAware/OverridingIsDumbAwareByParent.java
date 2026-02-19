import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;

public class OverridingIsDumbAwareByParent extends ByParent  {
}

abstract class ByParent implements PossiblyDumbAware {

  @Override
  public boolean isDumbAware() {
    return true;
  }

}
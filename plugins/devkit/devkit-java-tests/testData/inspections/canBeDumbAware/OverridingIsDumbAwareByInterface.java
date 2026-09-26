import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;

public class OverridingIsDumbAwareByInterface implements ByInterface {
}

interface ByInterface extends PossiblyDumbAware {

  @Override
  default boolean isDumbAware() {
    return true;
  }

}
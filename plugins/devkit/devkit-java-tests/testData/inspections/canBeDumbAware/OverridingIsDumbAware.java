import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;

public class OverridingIsDumbAware implements PossiblyDumbAware {
  @Override
  public boolean isDumbAware() {
    return PossiblyDumbAware.super.isDumbAware();
  }
}
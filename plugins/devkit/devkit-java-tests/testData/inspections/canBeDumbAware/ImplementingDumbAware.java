import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;

public class ImplementingDumbAware implements PossiblyDumbAware, DumbAware {
  // explicit override does not matter
  @Override
  public boolean isDumbAware() {
    return PossiblyDumbAware.super.isDumbAware();
  }
}
package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/**
 * APP
 */
@Service
public final class LightServiceEmpty {
  public static LightServiceEmpty getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceEmpty.class);
  }
}

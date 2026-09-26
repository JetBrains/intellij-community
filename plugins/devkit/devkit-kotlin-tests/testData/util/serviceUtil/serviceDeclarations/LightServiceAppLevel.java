package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/**
 * APP
 */
@Service(Service.Level.APP)
public final class LightServiceAppLevel {
  public static LightServiceAppLevel getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceAppLevel.class);
  }
}

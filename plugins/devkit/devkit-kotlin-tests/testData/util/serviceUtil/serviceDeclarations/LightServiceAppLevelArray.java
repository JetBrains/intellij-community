package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/**
 * APP
 */
@Service({Service.Level.APP})
public final class LightServiceAppLevelArray {
  public static LightServiceAppLevelArray getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceAppLevelArray.class);
  }
}

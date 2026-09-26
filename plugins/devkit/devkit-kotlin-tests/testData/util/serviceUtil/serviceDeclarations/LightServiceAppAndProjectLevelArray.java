package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/**
 * APP_AND_PROJECT
 */
@Service({Service.Level.APP, Service.Level.PROJECT})
public final class LightServiceAppAndProjectLevelArray {
   public static LightServiceAppAndProjectLevelArray getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceAppAndProjectLevelArray.class);
  }
}

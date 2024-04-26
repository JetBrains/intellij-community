package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/**
 * APP_AND_PROJECT
 */
@Service({Service.Level.APP, Service.Level.PROJECT, Service.Level.APP, Service.Level.PROJECT})
public final class LightServiceAppAndProjectLevelRepeatedArray {
   public static LightServiceAppAndProjectLevelRepeatedArray getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceAppAndProjectLevelRepeatedArray.class);
  }
}

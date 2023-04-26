package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

// Application Service by annotation
@Service({Service.Level.APP, Service.Level.PROJECT})
public final class LightServiceAppAndProjectLevelAnnotation {
   public static LightServiceAppAndProjectLevelAnnotation getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceAppAndProjectLevelAnnotation.class);
  }
}

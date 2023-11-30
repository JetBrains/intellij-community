package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

// Application Service by annotation
@Service(Service.Level.APP)
public final class LightServiceAppLevelAnnotation {
  public static LightServiceAppLevelAnnotation getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceAppLevelAnnotation.class);
  }
}

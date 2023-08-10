package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

// Application Service by empty annotation
@Service
public final class LightServiceEmptyAnnotation {
  public static LightServiceEmptyAnnotation getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceEmptyAnnotation.class);
  }
}

package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

// Application Service by default annotation
@Service({})
public final class LightServiceDefaultAnnotation {
  public static LightServiceDefaultAnnotation getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceDefaultAnnotation.class);
  }
}
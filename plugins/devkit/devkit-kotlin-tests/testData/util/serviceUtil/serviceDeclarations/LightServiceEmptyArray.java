package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;

/**
 * NOT_SPECIFIED
 */
@Service({})
public final class LightServiceEmptyArray {
  public static LightServiceEmptyArray getInstance() {
    return ApplicationManager.getApplication().getService(LightServiceEmptyArray.class);
  }
}

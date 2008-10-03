package org.jetbrains.plugins.gant;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLoader;

/**
 * @author ilyas
 */
public class GantLoader implements ApplicationComponent {

  public GantLoader(GroovyLoader loader){
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Gant loader";
  }

  public void initComponent() {

  }

  public void disposeComponent() {
  }
}

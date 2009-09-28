package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.gant.GantPropertiesInsertHandler;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.extensions.completion.InsertHandlerRegistry;

/**
 * @author ilyas
 */
public class GantLoader implements ApplicationComponent {

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Gant loader";
  }

  public void initComponent() {
    GroovyTemplatesFactory.getInstance().registerCustromTemplates("GantScript.gant");

    InsertHandlerRegistry.getInstance().registerSpecificInsertHandler(new GantPropertiesInsertHandler());
  }

  public void disposeComponent() {
  }
}

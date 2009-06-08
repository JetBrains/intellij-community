package org.jetbrains.plugins.gant;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gant.completion.GantPropertiesInsertHandler;
import org.jetbrains.plugins.gant.psi.GantScriptMembersProvider;
import org.jetbrains.plugins.gant.util.GantScriptDetector;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager;
import org.jetbrains.plugins.groovy.extensions.completion.InsertHandlerRegistry;
import org.jetbrains.plugins.groovy.extensions.resolve.ScriptMembersProviderRegistry;
import org.jetbrains.plugins.groovy.extensions.script.ScriptDetectorRegistry;

/**
 * @author ilyas
 */
public class GantLoader implements ApplicationComponent {

  static {
    GroovyPositionManager.GROOVY_EXTENSIONS.add(GantFileType.DEFAULT_EXTENSION);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Gant loader";
  }

  public void initComponent() {
    //Register Gant detector
    ScriptDetectorRegistry.getInstance().registerDetector(new GantScriptDetector());

    // Register Gant members provider
    ScriptMembersProviderRegistry.getInstance().registerProvider(new GantScriptMembersProvider());
    GroovyTemplatesFactory.getInstance().registerCustromTemplates("GantScript.gant");

    InsertHandlerRegistry handlerRegistry = InsertHandlerRegistry.getInstance();
    handlerRegistry.registerSpecificInsertHandler(new GantPropertiesInsertHandler());

  }

  public void disposeComponent() {
  }
}

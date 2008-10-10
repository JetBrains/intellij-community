package org.jetbrains.plugins.gant;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gant.debugger.GantPositionManagerHelper;
import org.jetbrains.plugins.gant.util.GantScriptDetector;
import org.jetbrains.plugins.gant.psi.GantScriptMembersPropertiesProvider;
import org.jetbrains.plugins.groovy.GroovyLoader;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelperRegistry;
import org.jetbrains.plugins.groovy.extensions.resolve.ScriptMembersProviderRegistry;
import org.jetbrains.plugins.groovy.extensions.script.ScriptDetectorRegistry;

/**
 * @author ilyas
 */
public class GantLoader implements ApplicationComponent {

  public GantLoader(GroovyLoader loader){
  }

  static {
    GroovyLoader.GROOVY_EXTENSIONS.add(GantFileType.DEFAULT_EXTENSION);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Gant loader";
  }

  public void initComponent() {
    // Register debugger position manager
    ScriptPositionManagerHelperRegistry.getInstance().registerPositionManagerHelper(new GantPositionManagerHelper());

    //Register Gant detector
    ScriptDetectorRegistry.getInstance().registerDetector(new GantScriptDetector());

    // Register Gant members provider
    ScriptMembersProviderRegistry.getInstance().registerProvider(new GantScriptMembersPropertiesProvider());
    GroovyTemplatesFactory.getInstance().registerCustromTemplates("GantScript.gant");

  }

  public void disposeComponent() {
  }
}

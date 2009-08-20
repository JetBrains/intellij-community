package org.jetbrains.plugins.groovy.extensions.script;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class ScriptDetectorRegistry implements ApplicationComponent {

  private final ArrayList<GroovyScriptType> myTypes = new ArrayList<GroovyScriptType>();

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ScriptDetectorRegistry";
  }

  public static ScriptDetectorRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ScriptDetectorRegistry.class);
  }

  public void registerDetector(GroovyScriptType type) {
    myTypes.add(type);
  }

  public GroovyScriptType[] getScriptDetectors() {
    return myTypes.toArray(new GroovyScriptType[myTypes.size()]);
  }


  public void initComponent() {

  }

  public void disposeComponent() {

  }
}

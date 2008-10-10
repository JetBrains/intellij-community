package org.jetbrains.plugins.groovy.extensions.script;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class ScriptDetectorRegistry implements ApplicationComponent {

  private ArrayList<GroovyScriptDetector> myDetectors = new ArrayList<GroovyScriptDetector>();

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ScriptDetectorRegistry";
  }

  public static ScriptDetectorRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ScriptDetectorRegistry.class);
  }

  public void registerPositionManagerHelper(GroovyScriptDetector detector) {
    myDetectors.add(detector);
  }

  public GroovyScriptDetector[] getScriptDetectors() {
    return myDetectors.toArray(new GroovyScriptDetector[myDetectors.size()]);
  }


  public void initComponent() {

  }

  public void disposeComponent() {

  }
}

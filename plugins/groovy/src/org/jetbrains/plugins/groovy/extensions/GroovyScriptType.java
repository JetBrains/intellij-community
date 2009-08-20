package org.jetbrains.plugins.groovy.extensions;

import com.intellij.execution.Location;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.AbstractGroovyScriptRunConfiguration;

import javax.swing.*;

/**
 * @author ilyas
 */
public abstract class GroovyScriptType {
  public static final ExtensionPointName<GroovyScriptTypeEP> EP_NAME = ExtensionPointName.create("org.intellij.groovy.scriptType");
  private static final GroovyScriptType DEFAULT_TYPE = new GroovyScriptType() {
    @Override
    public boolean isSpecificScriptFile(GroovyFile file) {
      return true;
    }

    @NotNull
    @Override
    public Icon getScriptIcon() {
      return GroovyFileType.GROOVY_LOGO;
    }
  };

  @NotNull
  public static GroovyScriptType getScriptType(@NotNull GroovyFile script) {
    assert script.isScript();
    for (final GroovyScriptTypeEP typeEP : EP_NAME.getExtensions()) {
      final GroovyScriptType descriptor = typeEP.getTypeDescriptor();
      if (descriptor.isSpecificScriptFile(script)) {
        return descriptor;
      }
    }
    return DEFAULT_TYPE;
  }


  public abstract boolean isSpecificScriptFile(GroovyFile file);

  @NotNull
  public abstract Icon getScriptIcon();

  public void tuneConfiguration(@NotNull GroovyFile file, @NotNull AbstractGroovyScriptRunConfiguration configuration, Location location) {
  }
}

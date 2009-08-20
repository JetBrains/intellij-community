package org.jetbrains.plugins.groovy.extensions.script;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

/**
 * @author ilyas
 */
public interface GroovyScriptDetector {

  boolean isSpecificScriptFile(GroovyFile file);

  @NotNull
  Icon getScriptIcon();

}

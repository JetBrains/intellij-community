package org.jetbrains.plugins.groovy.extensions.script;

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author ilyas
 */
public interface GroovyScriptDetector {

  boolean isSpecificScriptFile(GroovyFile file);

  @NotNull
  Icon getScriptIcon();

  @NotNull
  String getScriptExtension();

}

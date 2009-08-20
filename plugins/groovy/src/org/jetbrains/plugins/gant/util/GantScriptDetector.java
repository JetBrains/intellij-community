package org.jetbrains.plugins.gant.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gant.GantIcons;
import org.jetbrains.plugins.groovy.extensions.script.GroovyScriptDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.swing.*;

/**
 * @author ilyas
 */
public class GantScriptDetector implements GroovyScriptDetector {

  public boolean isSpecificScriptFile(final GroovyFile file) {
    return GantUtils.isGantScriptFile(file);
  }

  @NotNull
  public Icon getScriptIcon() {
    return GantIcons.GANT_ICON_16x16;
  }

}

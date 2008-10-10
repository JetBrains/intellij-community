package org.jetbrains.plugins.groovy.extensions.script;

import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public interface GroovyScriptDetector {

  boolean isSpecificScriptfile(GroovyFile file);

}

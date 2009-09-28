package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GrScriptClassNameIndex extends StringStubIndexExtension<GroovyFile> {
  public static final StubIndexKey<String, GroovyFile> KEY = StubIndexKey.createIndexKey("gr.script.class");

  public StubIndexKey<String, GroovyFile> getKey() {
    return KEY;
  }
}

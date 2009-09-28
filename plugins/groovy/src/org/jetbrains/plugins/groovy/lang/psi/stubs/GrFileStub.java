package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public interface GrFileStub extends PsiFileStub<GroovyFile> {
  StringRef getPackageName();

  StringRef getName();

  boolean isScript();
}

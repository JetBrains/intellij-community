package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrFileStubImpl;

/**
 * @author ilyas
 */
public class GroovyFileStubBuilder extends DefaultStubBuilder {
  protected StubElement createStubForFile(final PsiFile file) {
    if (file instanceof GroovyFile && ((GroovyFile) file).isScript()) {
      return new GrFileStubImpl((GroovyFile)file);
    }

    return super.createStubForFile(file);
  }
}
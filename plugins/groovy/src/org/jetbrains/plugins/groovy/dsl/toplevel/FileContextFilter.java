package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

/**
 * @author peter
 */
public class FileContextFilter implements ContextFilter {
  private final ElementPattern<? extends PsiFile> myPattern;

  public FileContextFilter(ElementPattern<? extends PsiFile> pattern) {
    myPattern = pattern;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    return myPattern.accepts(descriptor.getPlaceFile(), ctx);
  }

}
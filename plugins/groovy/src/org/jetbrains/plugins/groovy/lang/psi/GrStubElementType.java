package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ilyas
 */
public abstract class GrStubElementType<S extends StubElement, T extends GroovyPsiElement> extends IStubElementType<S, T> {

  public GrStubElementType(@NonNls @NotNull String debugName) {
    super(debugName, GroovyFileType.GROOVY_LANGUAGE);
  }

  public abstract PsiElement createElement(final ASTNode node);

  public void indexStub(final S stub, final IndexSink sink) {
  }

  public String getExternalId() {
    return "gr." + super.toString();
  }

}

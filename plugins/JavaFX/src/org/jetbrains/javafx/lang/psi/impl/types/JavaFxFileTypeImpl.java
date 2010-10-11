package org.jetbrains.javafx.lang.psi.impl.types;

import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.javafx.lang.psi.types.JavaFxFileType;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */

// TODO: better way?
public class JavaFxFileTypeImpl extends JavaFxType implements JavaFxFileType {
  private final PsiFile myFile;

  public JavaFxFileTypeImpl(PsiFile file) {
    myFile = file;
  }

  public PsiFile getFile() {
    return myFile;
  }

  @Override
  public String getPresentableText() {
    return myFile.getName();
  }

  @Override
  public String getCanonicalText() {
    return getPresentableText();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myFile.getResolveScope();
  }
}

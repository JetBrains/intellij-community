// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DotEnvProperty extends PsiElement {

  @NotNull
  DotEnvKey getKey();

  @Nullable
  DotEnvValue getValue();

  String getKeyText();

  String getValueText();

}

// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import java.util.List;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DotEnvProperty extends DotEnvNamedElement {

  @NotNull
  DotEnvKey getKey();

  @Nullable
  DotEnvValue getValue();

  @NlsSafe String getKeyText();

  @NlsSafe String getValueText();

  String getName();

  PsiElement setName(@NotNull String newName);

  PsiElement getNameIdentifier();

}

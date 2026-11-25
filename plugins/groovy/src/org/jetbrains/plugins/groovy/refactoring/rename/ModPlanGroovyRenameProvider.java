// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.model.SymbolPointer;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import com.intellij.refactoring.rename.ModPlanJavaRenameProvider;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * The rename of a Groovy element on the ModPlan engine whose legacy processor is a Java processor or a subclass of one. The Java provider
 * gives the usages, the analysis and the edits of the declaration, as the legacy Groovy processor runs the Java one as its super.
 * <p>
 * Each subclass is the provider of one legacy processor. A Groovy element belongs to the provider of the exact processor that
 * {@code RenamePsiElementProcessor.forElement} gives, as a Groovy processor extends another one: {@code RenameGrFieldProcessor} extends
 * {@code RenameJavaVariableProcessor}.
 */
@ApiStatus.Internal
public abstract class ModPlanGroovyRenameProvider extends ModPlanJavaRenameProvider {
  protected ModPlanGroovyRenameProvider(@NotNull Class<? extends RenamePsiElementProcessor> processorClass) {
    super(processorClass);
  }

  /** The legacy rename gives {@code subject}, a Groovy element, to the processor of this provider, not to a subclass of it. */
  @Override
  public boolean isOwner(@NotNull SymbolPointer subject) {
    PsiElement element = subject.element();
    return element != null && isGroovy(element) && RenamePsiElementProcessor.forElement(element).getClass() == processorClass();
  }

  static boolean isGroovy(@NotNull PsiElement element) {
    return element.getLanguage().isKindOf(GroovyLanguage.INSTANCE);
  }

  /** The rename of a Groovy variable that is no field, such as a local variable or a parameter, which the Java processor renames. */
  @ApiStatus.Internal
  public static final class ModPlanGroovyVariableRenameProvider extends ModPlanGroovyRenameProvider {
    public ModPlanGroovyVariableRenameProvider() {
      super(RenameJavaVariableProcessor.class);
    }
  }

  /** The rename of a Groovy element that the default processor renames, such as a label or the implicit parameter {@code it}. */
  @ApiStatus.Internal
  public static final class ModPlanGroovyPlainRenameProvider extends ModPlanBridgeRenameProvider {
    public ModPlanGroovyPlainRenameProvider() {
      super(RenamePsiElementProcessor.DEFAULT.getClass());
    }

    @Override
    public boolean isOwner(@NotNull SymbolPointer subject) {
      PsiElement element = subject.element();
      return element != null && isGroovy(element) && super.isOwner(subject);
    }
  }
}

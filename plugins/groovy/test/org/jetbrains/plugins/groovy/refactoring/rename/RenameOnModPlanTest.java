// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions;
import com.intellij.testFramework.ModPlanRenameTestSupport;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;

/**
 * The tests of the legacy Groovy rename on the ModPlan engine. A legacy test gives the element to {@code RenameProcessor}, which runs the
 * automatic renamers with no checkbox.
 */
@TestApplication
public class RenameOnModPlanTest extends RenameTest {
  @Override
  protected void rename(PsiElement element, String newName, boolean searchInComments, boolean searchTextOccurrences) {
    ModPlanOptionValues options = ModPlanOptionValues.EMPTY
      .with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, searchInComments)
      .with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, searchTextOccurrences);
    ModPlanOptionValues withRenamers = ModPlanRenameOptions.withRenamers(options, ModPlanRenameOptions.renamerIds(factory -> false));
    ModPlanRenameTestSupport.renameOnModPlan(getProject(), element, newName, withRenamers);
  }

  @Override
  protected void renameAtCaret(String newName) {
    rename(getFixture().getElementAtCaret(), newName, false, false);
  }

  /** The rename of the field renames its implicit accessors, so the plan needs no second subject. */
  @Override
  protected void renameWithAccessor(GrField field, GrAccessorMethod accessor, String accessorNewName) {
    rename(field, "newName", true, true);
  }

  @Override
  protected void runRenameRefactoringWithAutomaticRenames(String newName) {
    runInEdtAndWait(() -> ModPlanRenameTestSupport.renameOnModPlan(
      getProject(), getFixture().getElementAtCaret(), newName, ModPlanRenameOptions.withRenamersOfSettings(ModPlanOptionValues.EMPTY)));
  }
}

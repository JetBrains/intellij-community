package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntReference {

  IntentionAction[] EMPTY_INTENTIONS = new IntentionAction[0];

  boolean shouldBeSkippedByAnnotator();

  void setShouldBeSkippedByAnnotator(boolean value);

  String getUnresolvedMessagePattern();

  @NotNull
  IntentionAction[] getFixes();

  @Nullable
  String getCanonicalRepresentationText();
}

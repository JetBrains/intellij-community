// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;

/**
 * @author Max Medvedev
 */
public class FormattingContext {
  private final CommonCodeStyleSettings mySettings;
  private final GroovyCodeStyleSettings myGroovySettings;
  private final AlignmentProvider myAlignmentProvider;

  private final boolean myForbidWrapping;
  private final boolean myForbidNewLineInSpacing;

  private final GroovyBlockProducer myGroovyBlockProducer;

  public FormattingContext(@NotNull CommonCodeStyleSettings settings,
                           @NotNull AlignmentProvider provider,
                           @NotNull GroovyCodeStyleSettings groovySettings,
                           boolean forbidWrapping,
                           boolean forbidNewLineInSpacing,
                           @NotNull GroovyBlockProducer producer) {
    mySettings = settings;
    myAlignmentProvider = provider;
    myGroovySettings = groovySettings;
    this.myForbidWrapping = forbidWrapping;
    this.myForbidNewLineInSpacing = forbidNewLineInSpacing;
    myGroovyBlockProducer = producer;
  }

  public FormattingContext(@NotNull CommonCodeStyleSettings settings,
                           @NotNull AlignmentProvider provider,
                           @NotNull GroovyCodeStyleSettings groovySettings,
                           boolean forbidWrapping) {
    this(settings, provider, groovySettings, forbidWrapping, false, GroovyBlockProducer.DEFAULT);
  }

  public CommonCodeStyleSettings getSettings() {
    return mySettings;
  }

  public AlignmentProvider getAlignmentProvider() {
    return myAlignmentProvider;
  }

  public GroovyCodeStyleSettings getGroovySettings() {
    return myGroovySettings;
  }

  public FormattingContext createContext(boolean forbidWrapping, boolean forbidNewLineInSpacing) {
    return new FormattingContext(
      mySettings,
      myAlignmentProvider,
      myGroovySettings,
      myForbidWrapping || forbidWrapping,
      myForbidNewLineInSpacing || forbidNewLineInSpacing,
      myGroovyBlockProducer
    );
  }

  public boolean isForbidWrapping() {
    return myForbidWrapping;
  }

  public boolean isForbidNewLineInSpacing() {
    return myForbidNewLineInSpacing;
  }

  public Block createBlock(final @NotNull ASTNode node,
                           final @NotNull Indent indent,
                           final @Nullable Wrap wrap) {
    return myGroovyBlockProducer.generateBlock(node, indent, wrap, this);
  }
}

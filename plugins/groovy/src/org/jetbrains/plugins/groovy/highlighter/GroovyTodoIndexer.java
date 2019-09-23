// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyFilterLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;

/**
 * @author Maxim.Medvedev
 */
public class GroovyTodoIndexer extends LexerBasedTodoIndexer {
  @NotNull
  @Override
  public Lexer createLexer(@NotNull OccurrenceConsumer consumer) {
    return new GroovyFilterLexer(new GroovyLexer(), consumer);
  }
}

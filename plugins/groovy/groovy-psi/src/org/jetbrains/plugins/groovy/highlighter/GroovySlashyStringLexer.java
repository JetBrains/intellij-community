/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.highlighter;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author Max Medvedev
 */
public class GroovySlashyStringLexer extends GroovyStringLexerBase {

  public GroovySlashyStringLexer() {
    super(GroovyTokenTypes.mREGEX_CONTENT);
  }

  @Override
  protected boolean checkForSimpleValidEscape(int start) {
    return charAt(start) == '\\' && start + 1 < getBufferEnd() && charAt(start + 1) == '/';
  }

  @Override
  protected boolean checkForInvalidSimpleEscape(int start) {
    return false;
  }

  @Override
  protected boolean checkForHexCodeStart(int start) {
    return charAt(start) == '\\' && start + 1 < getBufferEnd() && charAt(start + 1) == 'u';
  }
}
